package fullykioskbrowser

import me.biocomp.hubitat_ci.device.HubitatDeviceSandbox
import me.biocomp.hubitat_ci.validation.Flags
import org.quartz.CronExpression
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Pure-logic tests for the "Fully Kiosk Browser Controller" driver.
 *
 * Each helper covered here is the kernel of a bug found in the 1.8.0 review (2026-09-30) that only shows
 * in a rare setup: a password with special characters, a tablet whose MAC can't be resolved, a polling
 * interval of an hour or more, a volume of 0, the tablet's 0-255 brightness scale, a battery report that
 * a stream of events could starve. Orchestration (HTTP calls, sendEvent, runIn, schedule) is validated
 * on the hub.
 *
 * Loaded as-is via hubitat_ci with the shared flag set — see the root CLAUDE.md for why each flag is there.
 */
class FullyKioskBrowserSpec extends Specification {

    static final File DRIVER = new File(
            System.getProperty('driversRoot'),
            'fully-kiosk-browser/fullykioskbrowser-hubitat-driver/Drivers/FullyKioskBrowserController.groovy')

    @Shared
    def driver = new HubitatDeviceSandbox(DRIVER).run(validationFlags: [
            Flags.DontRunScript,
            Flags.DontRestrictGroovy,
            Flags.DontValidateMetadata,
            Flags.DontValidatePreferences,
            Flags.DontValidateDefinition,
            Flags.DontRequireParseMethodInDevice,
            Flags.AllowReadingNonInputSettings,
    ])

    /** Decodes a URL's query the way the tablet's HTTP server does. */
    private static Map<String, String> query(String url) {
        new URI(url).rawQuery.split('&').collectEntries { String pair ->
            def kv = pair.split('=', 2)
            [(URLDecoder.decode(kv[0], 'UTF-8')): URLDecoder.decode(kv.size() > 1 ? kv[1] : '', 'UTF-8')]
        }
    }

    private static CronExpression utc(String cron) {
        def expr = new CronExpression(cron)
        expr.timeZone = TimeZone.getTimeZone('UTC')
        return expr
    }

    /** Fire times of a cron over a span, starting at 2026-01-01T00:00:30Z. */
    private static List<Date> fires(CronExpression expr, int spanMinutes) {
        Date t = new Date(1767225630000L)
        Date end = new Date(t.time + spanMinutes * 60_000L)
        List<Date> result = []
        while (t.before(end) && result.size() < 600) {
            t = expr.getNextValidTimeAfter(t)
            result << t
        }
        return result
    }

    private static List<Long> gapsInMinutes(List<Date> times) {
        (1..<times.size()).collect { (times[it].time - times[it - 1].time).intdiv(60_000L) }
    }

    // --- URLs sent to the tablet -------------------------------------------------------------------

    @Unroll
    def "fkbUrl: the tablet receives the password '#password' intact"() {
        // Up to 1.7.x the password went raw into the URL: '&' split it, '#' cut the query off, '+' turned
        // into a space, and a space made the URL invalid — every command failed as if the password was wrong.
        when:
        Map q = query(driver.fkbUrl('192.168.100.27', '2323', password, 'cmd=deviceInfo', true))

        then:
        q.password == password
        q.cmd == 'deviceInfo'
        q.type == 'json'

        where:
        password << ['simple', 'p&ss', 'a#b', '1+2', 'with space', '100%', 'x=y?z', 'çá']
    }

    def "fkbUrl: image requests are sent without type=json"() {
        when:
        String url = driver.fkbUrl('10.0.0.5', 2323, 'pw', 'cmd=getCamshot', false)

        then:
        url.startsWith('http://10.0.0.5:2323/?')
        !query(url).containsKey('type')
        query(url).cmd == 'getCamshot'
    }

    def "urlEncode: a missing value becomes empty instead of crashing the command"() {
        expect:
        driver.urlEncode(null) == ''
        driver.urlEncode('a b&c') == 'a+b%26c'
    }

    def "maskPassword: trace logs never show the password"() {
        expect:
        driver.maskPassword('http://1.2.3.4:2323/?type=json&password=s%26cret&cmd=deviceInfo') ==
                'http://1.2.3.4:2323/?type=json&password=***&cmd=deviceInfo'
        driver.maskPassword('http://1.2.3.4:2323/?password=abc') == 'http://1.2.3.4:2323/?password=***'
    }

    @Unroll
    def "playSoundCmd: '#url' (loop=#loop) reaches the tablet intact"() {
        when:
        Map q = query("http://x/?${driver.playSoundCmd(url, loop)}")

        then:
        q.cmd == 'playSound'
        q.url == url
        q.containsKey('loop') == loop

        where:
        url                                | loop
        'http://hub/local/siren.mp3'       | true
        'http://hub/local/a b.mp3?x=1&y=2' | false
    }

    // --- Device network ID -------------------------------------------------------------------------

    @Unroll
    def "ipToHex('#ip') == #expected"() {
        // The hub routes a device's reports by DNI = MAC, or IP in hex. When the MAC couldn't be resolved,
        // up to 1.7.x the DNI became the dotted IP, which the hub never matches: reports were silently lost.
        expect:
        driver.ipToHex(ip) == expected

        where:
        ip               || expected
        '192.168.100.27' || 'C0A8641B'
        '10.0.0.1'       || '0A000001'
        ' 10.0.0.1 '     || '0A000001'
        '256.1.1.1'      || null
        '1.2.3'          || null
        '1..2.3'         || null
        '1.2.3.4.5'      || null
        'tablet.local'   || null
        ''               || null
        null             || null
    }

    // --- Polling schedule --------------------------------------------------------------------------

    @Unroll
    def "pollCron(#minutes) is a valid cron that fires every #minutes minutes, on second :00"() {
        when:
        List<Date> times = fires(utc(driver.pollCron(minutes)), 3 * 24 * 60)

        then:
        gapsInMinutes(times).every { it == minutes }
        times.every { it.time % 60_000L == 0 }  // on :00 — tells a poll from a device push in the event history

        where:
        minutes << [1, 2, 5, 10, 15, 30, 60, 120, 180, 360, 720, 1440]
    }

    @Unroll
    def "pollCron(#minutes) == null: no cron keeps that pace, so the driver chains runIn instead"() {
        expect:
        driver.pollCron(minutes) == null

        where:
        minutes << [7, 45, 90, 300, 600, 1000, 0, -5, null]
    }

    def "the cron 1.7.x built is invalid for an hour or more, and uneven for 45 minutes"() {
        // Documents the bug: 1.7.x always scheduled "0 0/<minutes> * * * ?", and the field accepts up to 1440.
        expect:
        !CronExpression.isValidExpression('0 0/60 * * * ?')
        !CronExpression.isValidExpression('0 0/120 * * * ?')
        gapsInMinutes(fires(utc('0 0/45 * * * ?'), 3 * 60)).toSet() == [45L, 15L].toSet()
    }

    // --- Volume ------------------------------------------------------------------------------------

    @Unroll
    def "volumeTarget(saved=#saved, current=#current, #delta) == #expected"() {
        // 1.7.x treated a volume of 0 as "no volume": volumeUp did nothing at 0.
        expect:
        driver.volumeTarget(saved, current, delta) == expected

        where:
        saved | current | delta || expected
        null  | 0       | 10    || 10
        null  | 95      | 10    || 100
        null  | 5       | -10   || 0
        40    | 0       | 10    || 50    // muted: steps from the volume mute() saved
        null  | null    | 10    || null  // nothing known: no command
        null  | '50'    | -10   || 40
    }

    @Unroll
    def "volumeToRestore(saved=#saved, current=#current) == #expected"() {
        // 1.7.x restored 100% after the siren (and after unmute) when the volume had been 0.
        expect:
        driver.volumeToRestore(saved, current) == expected

        where:
        saved | current || expected
        null  | 30      || 30
        null  | 0       || 0
        40    | 0       || 40
        0     | 50      || 0
        null  | null    || 100
        '35'  | null    || 35
    }

    @Unroll
    def "parseSpeakVolume(#volume) == #expected"() {
        expect:
        driver.parseSpeakVolume(volume) == expected

        where:
        volume || expected
        -1     || null  // the capability's "no volume given"
        '-1'   || null
        null   || null
        ''     || null
        50     || 50
        '70'   || 70
        150    || 100
        'abc'  || null
    }

    // --- Brightness --------------------------------------------------------------------------------

    @Unroll
    def "levelToBrightness(#level) == #expected: the tablet's brightness runs 0-255"() {
        // 1.7.x sent setLevel's 0-100 straight to the tablet: setLevel(50) gave ~20% brightness.
        expect:
        driver.levelToBrightness(level) == expected

        where:
        level || expected
        0     || 0
        50    || 128
        100   || 255
        150   || 255
        -5    || 0
        '40'  || 102
        null  || null
    }

    @Unroll
    def "brightnessToLevel(#brightness) == #expected"() {
        // A real tablet reported 108 (2026-09-30), which 1.7.x wrote straight into the 0-100 'level'.
        expect:
        driver.brightnessToLevel(brightness) == expected

        where:
        brightness || expected
        108        || 42
        255        || 100
        0          || 0
        300        || 100
        null       || null
    }

    // --- Battery report injected into the tablet ----------------------------------------------------

    def "batteryJs throttles: at most one report per interval, and a stream of events can't starve it"() {
        // The debounce tried before release reset its timer on every event: a device that fires
        // onBatteryLevelChanged non-stop would never report.
        when:
        String js = driver.batteryJs(30000)

        then:
        js.contains("fully.bind('onBatteryLevelChanged'")
        js.contains('30000')
        !js.contains('clearTimeout')  // a throttle never pushes its deadline back
        js.contains('900000')         // the 15-minute fallback report stays
    }

    // --- Parsing -----------------------------------------------------------------------------------

    @Unroll
    def "toIntOrNull(#val) == #expected"() {
        expect:
        driver.toIntOrNull(val) == expected

        where:
        val    || expected
        5      || 5
        '7'    || 7
        '55.0' || 55
        42.6   || 43
        ' 8 '  || 8
        ''     || null
        'abc'  || null
        null   || null
    }

    // --- Power source (1.9.0) ----------------------------------------------------------------------

    @Unroll
    def "powerSourceOf(#json) == #expected"() {
        // Asked in the forum (Greenwave, 2026-11-01): plugged/unplugged. The poll's deviceInfo already
        // carries it — read on a Fire HD with Fully 1.60: isPlugged=false, plugged=false, batteryLevel=55.
        expect:
        driver.powerSourceOf(json) == expected

        where:
        json                              || expected
        [isPlugged: true]                 || 'mains'
        [isPlugged: false]                || 'battery'
        [isPlugged: false, plugged: true] || 'battery'  // isPlugged is the one Fully documents; it wins
        [plugged: true]                   || 'mains'    // a version that only sends 'plugged'
        [plugged: false]                  || 'battery'
        [batteryLevel: 55]                || null       // not reported: no event, never a guess
        [isPlugged: null]                 || null
        [isPlugged: 'yes']                || null       // not a boolean: unknown, no event
        null                              || null
    }

    @Unroll
    def "plugSuffix(#source) == '#expected': the battery log reads like Fully's own '81.0% (unplugged)'"() {
        expect:
        driver.plugSuffix(source) == expected

        where:
        source    || expected
        'mains'   || ' (plugged)'
        'battery' || ' (unplugged)'
        'unknown' || ''
        null      || ''
    }
}
