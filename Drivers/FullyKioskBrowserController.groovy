// Fully Kiosk Browser Driver 1.8.0
// Github: https://github.com/esimioni/fullykioskbrowser-hubitat-driver

// Fork of Original Driver by Gavin Campbell 1.4.1:
// Github: https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy
// Original Support: https://community.hubitat.com/t/release-fully-kiosk-browser-controller/12223
/*
[Change Log]
    1.8.0 (unreleased): Battery level reports are throttled to at most one every 30 s, so devices that fire
            onBatteryLevelChanged dozens of times per second no longer flood the hub. The 15-minute fallback
            report is unchanged.
        : Server passwords with special characters (& # + % or a space) now work: the password is URL-encoded.
        : When the tablet's MAC address can't be found, the device network ID is now its IP address in hex, which
            the hub routes. The plain IP used before made the hub silently drop the tablet's reports.
        : Polling intervals of 60 minutes or more now work (they produced an invalid schedule), and intervals that
            don't divide an hour now poll at a steady pace.
        : Volume: volumeUp works from 0, mute remembers the right volume, and a volume of 0 is restored after the
            siren and after unmute (it came back as 100%).
        : Full capability contracts: take() captures a camera image, and setLevel() accepts a duration (ignored).
        : Breaking: setLevel() and the 'level' attribute now use 0-100% and are converted to the tablet's 0-255
            brightness (setLevel(50) was giving ~20%). setScreenBrightness still takes the raw 0-255 value.
        : speak() applies the requested volume with both TTS engines, and no longer fails when voice is null.
        : Runs single threaded (requires hub firmware 2.2.9 or newer), so concurrent executions can't overwrite each
            other's state, e.g. losing the volume to restore after the siren.
        : Commands that depend on each other are sent in order: the start page reloads only after the tablet stores
            the new script, and sounds play only after their volume is set.
        : Saving preferences no longer cancels a pending siren auto-stop, motion timeout or overlay/image clear.
        : A timed overlay message no longer clears a permanent one sent after it.
        : Polling no longer overwrites motion, acceleration, volume or screen state when the tablet doesn't report
            them, and the network status is checked right after saving instead of assumed online.
        : The server password is masked in the preferences and in trace logs. Minor fixes and cleanups.
        : After updating, click "Save Preferences" (or "Configure") on each device so the new script reaches the
            tablet. This reloads the tablet's start page.
    1.7.1: Added to HPM for easier installation and updates.
        : Logging system refactored to use numeric levels (1-5) with caching for improved performance.
        : Unified HTTP response validation via isResponseOk() with proper FKB error status detection.
        : Better error messages on wrong server password ('please login' detection).
        : Improved async callback handling for consistent response/data argument order.
        : Extracted poll failure tracking into dedicated recordPollFailure() method with named threshold constant.
        : Fixed potential crash in handleBatteryLevel when battery level is null.
        : clearImage now sets attribute to 'none' instead of empty string for consistency.
        : Standardized changelog version numbering to semver format.
    1.7.0: Added networkStatus attribute to track device connectivity status.
        : Added support for "Notification" capability with a toggle to display as overlay or toast.
        : Added commands to capture images from the device camera and screen, with support for base64 image storage in attributes.
            Screenshot is from the FKB screen, it can't access data from other apps.
        : Breaking: Removed HealthCheck capability and 'lastActivity' attribute as networkStatus provides better insight into connectivity.
            It wasn't working properly anyway.
        : Code cleanup and optimizations.
    1.6.0: Added a language preference option for TTS.
        : Added support to display overlay messages.
        : Added support to display "toast" notifications.
        : Added command to reboot the device. FKB needs to be provisioned as device owner: https://www.fully-kiosk.com/en/#provisioning
        : Better handling of device inclusion, showing clear warnings instead of misleading errors.
        : Clear warnings when trying to send commands without server details defined.
        : Commands 'on, off, screenOn, screenOff' now update the 'switch' attribute accordingly.
        : Warning shown if sirenFile is not defined when siren is triggered.
        : Auto clearing of alarm state after siren is triggered (configurable interval).
        : Breaking: Command 'off' no longer stops the siren, use 'sirenStop'.
        : Code improvements.
    1.5.0: Configurable polling interval.
        : States to be updated on Hubitat are now configurable.
        : JS Code injection updated to reduce CPU usage on the tablet and send only configured events.
        : Added cameraMotion event for camera-based motion detection.
        : Added commands to enable/disable touch input (disabling touch will not work on all scenarios)
        : Added commands to lock/unlock kiosk mode.
        : Log handling full refactoring.
        : Implemented closure-based logging for better performance.
        : Code cleanup and optimizations.
    1.4.1: Fixed speak command. Was broken with Hubitat firmware 2.9.0.
            This will allow it to work with RM and not give an error.
        : Volume will be set if specified (optional), and voice is passed to the engine (optional)
    1.4.0: Requires Fully Kiosk Browser 1.43.1 or newer.
        : Added auto configuration of webviewMixedContent
            This allows FKB to report in device status to HE from dashboards that use https.
            After upgrading click configure so all the settings get applied.
    1.3.9: Added attribute "currentPageUrl"
            This attribute is updated with the current page during polling (every minute).
    1.3.8: Fixed switch reporting.
    1.3.7: Added State Polling option to allow the driver to poll the device for updates instead of the device reporting in.
            This solves the issue where the start page is SSL. Reporting will not work back to a non SSL endpoint.
            This will gather the screen brightness,screen state and battery levels only.  Motion will not work.
    1.3.6: Added 'restartApp' command. (Thanks tmleafs)
    1.3.5: Added 'Battery' capability to track the ... battery.
        : Added 'Switch' and 'SwitchLevel capabilities to turn the screen on/off and adjust the brightness
        : Added 'AccelerationSensor' capability which triggers when tablet is moved.
        : Added 'updateDeviceData' method to record device settings when the preferences is saved.
        : Added 'HealthCheck' capability. Mainly used to help increment Last Activity when device is responding.
        : Removed lastActivity custom attribute. Reduces event log noise.
    1.3.3: Added 'MotionSensor' capability to monitor motion via the tablet camera.
        : deviceNetworkId will now be set to the MAC of the IP Address to handle callbacks from FKB
        : Fixed setStringSetting method
        : Added 'Configure' capability.
        :  When you select configure it will configure FKB on the device to send events back to this driver.
        :  Configure should be run when making configuration changes.
        :  WARNING: selecting this will overwrite any custom javascript code you currently have setup in fully.
    1.3.2: If using the FKB TTS Engine, starting text with "!" will cause all messages to be stopped and the new message
          to play. Otherwise the message is added to the queue and will play when others are finished. (Requires FKB v1.38+)
        : Sending a "!" TTS message will stop all currently playing messages to stop. (Requires FKB v1.38+)
    1.3.1: Updated to use "{ }" instead of "< />" for SSML tags.
    1.3.0: Added option to select the TTS engine used.
            Hubitat (Amazon): https://docs.aws.amazon.com/polly/latest/dg/supportedtags.html
            Fully Kiosk Browser (Google): https://cloud.google.com/text-to-speech/docs/ssml
    1.2.4: Added setBooleanSetting,setStringSetting
          Added lastActivity attribute
    1.2.3: Updated speak() logging to escape XML in logging as speak command can support SSML XML
    1.2.2: Updated HTTP calls so URL's are encoded properly
    1.2.1: Fixed the import url to be correct
    1.2.0: Change speak method to use Hubitat TTS methods. Set voice via Hubitat settings.
    1.0.9: Changed volumeStream range to be 1-10 (0 doesn't work)
        : Made adjustements to setVolume to properly test for volumeStream value
        : Added playSound/stopSound commands.
        : Added the AudioVolume mute attributes.
        :Set default attributes when installed.
*/

import groovy.transform.Field
import java.math.RoundingMode

metadata {
    // singleThreaded: without it, each execution loads a snapshot of state and saves the whole map when it ends,
    // so two overlapping executions (a command and a poll or a push from the tablet) could silently undo each
    // other's writes — e.g. lose state.siren and leave the volume at the siren's level. Requires HE 2.2.9+.
    definition(name: 'Fully Kiosk Browser Controller', namespace: 'esimioni', author: 'Eduardo Simioni', singleThreaded: true, importUrl: 'https://raw.githubusercontent.com/esimioni/fullykioskbrowser-hubitat-driver/refs/heads/master/Drivers/FullyKioskBrowserController.groovy') {
        capability 'Actuator'
        capability 'Alarm'
        capability 'AudioVolume'
        capability 'Refresh'
        capability 'SpeechSynthesis'
        capability 'Tone'
        capability 'Battery'
        capability 'Switch'
        capability 'SwitchLevel'
        capability 'MotionSensor'
        capability 'Configuration'
        capability 'AccelerationSensor'
        capability 'Notification'
        capability 'ImageCapture'

        attribute 'currentPageUrl', 'String'
        attribute 'cameraMotion', 'String'
        attribute 'image', 'string'
        attribute 'networkStatus', 'enum', ['online', 'offline', 'unknown']

        command 'bringFullyToFront'
        command 'launchAppPackage', ['String']
        command 'loadStartURL'
        command 'loadURL', ['String']
        command 'playSound', ['String']
        command 'restartApp'
        command 'rebootDevice'
        command 'screenOn'
        command 'screenOff'
        command 'setScreenBrightness', [[name:'Brightness*', type:'NUMBER', description:'Raw Fully Kiosk Browser value, 0-255. setLevel() takes 0-100%.']]
        command 'setOverlayMessage', [
            [name:'Message*', type:'STRING', description:'The message to display.'],
            [name:'Duration', type:'NUMBER', description:'Seconds to display the message (0 for permanent).']
        ]
        command 'clearOverlayMessage'
        command 'showToast', ['String']
        command 'sirenStop'
        command 'startScreensaver'
        command 'stopScreensaver'
        command 'stopSound'
        command 'triggerMotion'
        command 'touchEnable'
        command 'touchDisable'
        command 'lockKiosk'
        command 'unlockKiosk'
        command 'getCamshot'
        command 'getScreenshot'
        command 'setBooleanSetting', [[name:'Key*', type:'STRING', description:'The key value associated with the setting to be updated.'],
                                     [name:'Value*', type:'ENUM', constraints:['true', 'false'], description:'The setting to be applied.']]
        command 'setStringSetting', [[name:'Key*', type:'STRING', description:'The key value associated with the setting to be updated.'],
                                     [name:'Value*', type:'STRING', description:'The setting to be applied.']]
    }
    preferences {
        input(name:'serverIP', type:'string', title:'Server IP Address', description:'Device IP', defaultValue:'', required:true)
        input(name:'serverPort', type:'string', title:'Server Port', defaultValue:'2323', required:true)
        input(name:'serverPassword', type:'password', title:'Server Password', defaultValue:'', required:true)
        input(name:'toneFile', type:'string', title:'Tone Audio File URL', defaultValue:'', required:false)
        input(name:'sirenFile', type:'string', title:'Siren Audio File URL', defaultValue:'', required:false)
        input(name:'sirenVolume', type:'integer', title:'Siren Volume (0-100)', range:[0..100], defaultValue:'100', required:false)
        input(name:'volumeStream', type:'enum', title:'Volume Stream',
              options:['1':'System', '2':'Ring', '3':'Music', '4':'Alarm', '5':'Notification', '6':'Bluetooth', '7':'System Enforced', '8':'DTMF', '9':'TTS', '10':'Accessibility'],
              defaultValue:'1',required:true,multiple:false)
        input(name:'ttsEngine', type:'enum', title:'TTS Engine', description:'Select the TTS engine that is used.', options:[0:'Hubitat', 1:'Fully Kiosk Browser'], defaultValue:1, required:true)
        input(name:'ttsLanguage', type:'String', title:'TTS Language', description:'Defines the <a href="https://docs.oracle.com/cd/E13214_01/wli/docs92/xref/xqisocodes.html" target="_blank">language</a> for TTS (ex: en_AU, pt_BR, fr_MU).', defaultValue:'', required:false)
        input(name:'motionTimeout', type:'number', title:'Motion/Acceleration Timeout', description:'Number of seconds before motion/acceleration is reset to inactive.', defaultValue:30, required:true)
        input(name:'pollInterval', type:'number', title:'Polling Interval', description:'Minutes - Enable this option to force polling of the device states. 0 disables it.', range:[0..1440], defaultValue:5, required:true)
        input(name:'clearAlarmInterval', type:'number', title:'Clear Alarm State Interval', description:'Seconds - Enable this option to automatically clear the alarm state and stop the siren after triggering the siren. 0 disables it.', range:[0..86400], defaultValue:60, required:true)
        input(name:'notificationType', type:'enum', title:'Notification Type', description:'How to display notifications sent via the device.', options:['overlay':'Overlay Message', 'toast':'Toast Notification'], defaultValue:'overlay', required:true)
        input(name:'imageClearInterval', type:'number', title:'Auto-Clear Image Interval', description:'Seconds - Automatically clear the captured image from current states after this interval. 0 disables it.', range:[0..86400], defaultValue:0, required:true)
        input(name:'reportMotion', type: 'bool', title: 'Report Motion (Sensors)', defaultValue: false)
        input(name:'reportCameraMotion', type: 'bool', title: 'Report Camera Motion', defaultValue: false)
        input(name:'reportAcceleration', type: 'bool', title: 'Report Acceleration', defaultValue: false)
        input(name:'reportVolume', type: 'bool', title: 'Report Volume', defaultValue: false)
        input(name:'reportSwitch', type: 'bool', title: 'Report Screen On/Off', defaultValue: true)
        input(name:'reportBattery', type: 'bool', title: 'Report Battery Level', defaultValue: true)
        input(name:'reportBrightness', type: 'bool', title: 'Report Screen Brightness', defaultValue: false)
        input(name:'reportPageUrl', type: 'bool', title: 'Report Current Page URL', defaultValue: false)
        input(name:'loggingLevel', type:'enum', title:'Logging level', options: ['1':'Error', '2':'Warning', '3':'Info', '4':'Debug', '5':'Trace'], defaultValue: '3', required: true)
    }
}


@Field static final int POLL_FAILURE_THRESHOLD = 3
@Field static final int BATTERY_REPORT_INTERVAL_MS = 30000
@Field static final String CMD_LOAD_START_URL = 'cmd=loadStartURL'

@Field int cachedLoggingLevel = -1

// *** [ Initialization Methods ] *********************************************
def installed() {
    logger('I', '[installed]')
    checkServerDefined() // Just to show a warning to set device IP Address
}

def updated() {
    logger('I', '[updated]')
    initialize()
}

def initialize() {
    logger('I', '[initialize]')
    updateCachedLoggingLevel()
    // Only the polling jobs: a full unschedule() also cancelled a pending siren auto-stop, motion timeout or
    // overlay/image clear, leaving the siren looping or motion stuck 'active' after a Save Preferences.
    unschedule('refresh')
    unschedule('pollTick')
    updateDeviceNetworkId()
    schedulePolling()
    updateDeviceData()
    configure()
}

private void updateDeviceNetworkId() {
    def mac = settings.serverIP ? getMACFromIP("${settings.serverIP}") : null
    String ipHex = ipToHex(settings.serverIP)
    if (mac) {
        logger('I', {"MAC address found. Updating deviceNetworkId: ${mac}"})
        device.deviceNetworkId = mac
    } else if (ipHex) {
        // The hub routes the tablet's reports by DNI = MAC, or IP in hex; it never matches a dotted IP.
        logger('I', {"MAC address not found. Setting deviceNetworkId to the IP address in hex: ${ipHex}"})
        device.deviceNetworkId = ipHex
    } else if (settings.serverIP) {
        logger('W', {"Server IP '${settings.serverIP}' is not an IPv4 address, so the tablet's reports can't reach this device. Use the tablet's IP address."})
        device.deviceNetworkId = settings.serverIP
    } else {
        device.deviceNetworkId = "TBD-${device.id}"
        logger('W', {'Server IP not set. DNI temporarily set to "TBD-[deviceId]". Please set Server IP in preferences.'})
    }
}

private void schedulePolling() {
    state.pollFailures = 0
    int minutes = safeToInt(settings.pollInterval, 0)
    if (minutes <= 0) {
        sendEvent([name:'networkStatus', value:'unknown'])
        logger('I', {'Polling disabled. Network status set to unknown.'})
        return
    }
    String cron = pollCron(minutes)
    if (cron) {
        schedule(cron, 'refresh')
    } else {
        runIn(minutes * 60, 'pollTick')
    }
    logger('I', {"Polling scheduled every ${minutes} minutes."})
    if (device.currentValue('networkStatus') == null) {
        sendEvent([name:'networkStatus', value:'unknown'])
    }
    // Check the tablet now, instead of assuming it's online until the first scheduled poll.
    runIn(3, 'initialPoll')
}

/** Polls at an interval no cron can keep (see pollCron): each poll schedules the next one before it runs. */
def pollTick() {
    int minutes = safeToInt(settings.pollInterval, 0)
    if (minutes > 0 && pollCron(minutes) == null) {
        runIn(minutes * 60, 'pollTick')
    }
    refresh()
}

def initialPoll() {
    refresh()
}

def configure() {
    logger('I', '[configure]')
    setBooleanSetting('websiteIntegration', true)
    setStringSetting('webviewMixedContent', '0')

    def jsCode = """
    function sendAttributeValue(attribute,value) {
        var xhr = new XMLHttpRequest();
        xhr.open("POST","http://${location.hub.localIP}:39501",true);
        let httpData = {};
        if (attribute=='volume') {
            httpData = {attribute:attribute,value:fully.getAudioVolume(value)};
        } else if (attribute=='battery') {
            httpData = {attribute:attribute,value:fully.getBatteryLevel()};
        } else {
            httpData = {attribute:attribute,value:value};
        };
        xhr.send(JSON.stringify(httpData));
    };
    """
    if (settings.reportMotion) jsCode += "fully.bind('onMotion','sendAttributeValue(\\'motion\\',\\'active\\');');\n"
    if (settings.reportCameraMotion) jsCode += "fully.bind('onMotion','sendAttributeValue(\\'cameraMotion\\',\\'active\\');');\n"
    if (settings.reportAcceleration) jsCode += "fully.bind('onMovement','sendAttributeValue(\\'acceleration\\',\\'active\\');');\n"
    if (settings.reportVolume) {
        jsCode += "fully.bind('volumeUp','sendAttributeValue(\\'volume\\',${settings.volumeStream});');\n"
        jsCode += "fully.bind('volumeDown','sendAttributeValue(\\'volume\\',${settings.volumeStream});');\n"
    }
    if (settings.reportSwitch) {
        jsCode += "fully.bind('screenOn','sendAttributeValue(\\'switch\\',\\'on\\');');\n"
        jsCode += "fully.bind('screenOff','sendAttributeValue(\\'switch\\',\\'off\\');');\n"
        // Also bind to onScreenOn/onScreenOff for better reliability
        jsCode += "fully.bind('onScreenOn','sendAttributeValue(\\'switch\\',\\'on\\');');\n"
        jsCode += "fully.bind('onScreenOff','sendAttributeValue(\\'switch\\',\\'off\\');');\n"
    }
    if (settings.reportBattery) {
        jsCode += batteryJs(BATTERY_REPORT_INTERVAL_MS)
    }

    setBooleanSetting('motionDetection', settings.reportMotion == true || settings.reportCameraMotion == true)
    setBooleanSetting('movementDetection', settings.reportAcceleration == true)
    // The page runs injectJsCode only when it loads, and the commands are asynchronous: reload the start page
    // only after the tablet has stored the new code, or a reload that wins the race keeps the old script.
    sendStringSetting('injectJsCode', jsCode, CMD_LOAD_START_URL)
}

/**
 * The battery part of the injected script. Throttled: onBatteryLevelChanged can fire dozens of times per second
 * on some devices, so a change arms one report intervalMs later and the events until then ride along. Unlike a
 * debounce, a device that never stops firing still reports once per interval. Plus a 15-minute fallback report.
 */
String batteryJs(int intervalMs) {
    return "var _battTimer=null; function _throttleBatt(){if(_battTimer)return;_battTimer=setTimeout(function(){_battTimer=null;sendAttributeValue('battery','');},${intervalMs});}\n" +
            "fully.bind('onBatteryLevelChanged','_throttleBatt();');\n" +
            "setInterval(function(){sendAttributeValue('battery','');}, 900000);\n"
}

// *** [ Parsing Methods ] ****************************************************
def parse(description) {
    logger('T', {"parse description: ${description}"})
    def msg = parseLanMessage(description)
    def body = msg.body
    body = parseJson(body)
    logger('T', {"body: ${body}"})
    switch (body.attribute) {
        case 'switch':
            sendEvent([name:'switch', value:body.value])
            break
        case 'battery':
            handleBatteryLevel(body.value)
            break
        case 'motion':
            motion(body.value)
            break
        case 'cameraMotion':
            cameraMotion(body.value)
            break
        case 'acceleration':
            acceleration(body.value)
            break
        case 'volume':
            sendEvent([name:'volume', value:body.value])
            break
        default:
            logger('E', {"Unknown attribute: ${body.attribute}"})
            break
    }
}

def motion(value) {
    reportTimedActivity('motion', value, 'motionInactive')
}

def motionInactive() {
    sendEvent([name:'motion', value:'inactive'])
}

def cameraMotion(value) {
    reportTimedActivity('cameraMotion', value, 'cameraMotionInactive')
}

def cameraMotionInactive() {
    sendEvent([name:'cameraMotion', value:'inactive'])
}

def acceleration(value) {
    reportTimedActivity('acceleration', value, 'accelerationInactive')
}

def accelerationInactive() {
    sendEvent([name:'acceleration', value:'inactive'])
}

/** Sends the event and, when active, schedules the handler that resets it after the motion timeout. */
private void reportTimedActivity(String attribute, value, String inactiveHandler) {
    logger('D', {"[${attribute}] value: ${value}"})
    sendEvent([name:attribute, value:value])
    if (value == 'active') {
        // Defaults to 30 s when the preference was never saved (e.g. right after upgrading from an older version).
        runIn(safeToInt(settings.motionTimeout, 30), inactiveHandler)
    }
}

// *** [ Device Methods ] *****************************************************
def on() {
    logger('D', '[on]')
    screenOn()
}

def off() {
    logger('D', '[off]')
    screenOff()
}

/** SwitchLevel: level is 0-100%, the tablet's brightness 0-255. The capability's duration is accepted and ignored. */
def setLevel(level, duration = null) {
    logger('D', {"[setLevel] level: ${level}"})
    Integer brightness = levelToBrightness(level)
    if (brightness == null) {
        logger('W', {"setLevel: '${level}' is not a number"})
        return
    }
    setScreenBrightness(brightness)
    sendEvent([name:'level', value:brightnessToLevel(brightness), unit:'%'])
}

def deviceNotification(message) {
    logger('D', {"[deviceNotification] message:${message}"})
    if (settings.notificationType == 'toast') {
        showToast(message)
    } else {
        setOverlayMessage(message, 10)
    }
}

def beep() {
    logger('D', '[beep]')
    if (!toneFile) {
        logger('W', 'Tone Audio File URL not set in preferences')
        return
    }
    sendCommandPost(playSoundCmd(toneFile))
}

def launchAppPackage(appPackage) {
    logger('D', '[launchAppPackage]')
    sendCommandPost("cmd=startApplication&package=${urlEncode(appPackage)}")
}

def bringFullyToFront() {
    logger('D', '[bringFullyToFront]')
    sendCommandPost('cmd=toForeground')
}

def restartApp() {
    logger('D', '[restartApp]')
    sendCommandPost('cmd=restartApp')
}

def rebootDevice() {
    logger('I', '[rebootDevice]')
    sendCommandPost('cmd=rebootDevice')
}

def screenOn() {
    logger('D', '[screenOn]')
    sendCommandPost('cmd=screenOn')
    sendEvent([name:'switch', value:'on'])
}

def screenOff() {
    logger('D', '[screenOff]')
    sendCommandPost('cmd=screenOff')
    sendEvent([name:'switch', value:'off'])
}

def setScreenBrightness(value) {
    logger('D', {"[setScreenBrightness]: ${value}"})
    sendCommandPost("cmd=setStringSetting&key=screenBrightness&value=${value}")
}

def setOverlayMessage(text, duration=0) {
    logger('D', {"[setOverlayMessage] text:${text}, duration:${duration}"})
    // A pending clear belongs to the previous message: left alone it would erase this one, even a permanent one.
    unschedule('clearOverlayMessage')
    sendCommandPost("cmd=setOverlayMessage&text=${urlEncode(text)}")
    int seconds = safeToInt(duration, 0)
    if (seconds > 0) {
        runIn(seconds, 'clearOverlayMessage')
    }
}

def clearOverlayMessage() {
    logger('D', '[clearOverlayMessage]')
    sendCommandPost('cmd=setOverlayMessage&text=')
}

def showToast(text) {
    logger('D', {"[showToast] text:${text}"})
    sendCommandPost("cmd=showToast&text=${urlEncode(text)}")
}

def triggerMotion() {
    logger('D', '[triggerMotion]')
    sendCommandPost('cmd=triggerMotion')
}

def touchEnable() {
    logger('D', '[touchEnable]')
    setBooleanSetting('disableAllTouch', false)
    setStringSetting('touchRestriction', '0')
}

def touchDisable() {
    logger('D', '[touchDisable]')
    setBooleanSetting('disableAllTouch', true)
    setStringSetting('touchRestriction', '1')
}

def lockKiosk() {
    logger('D', '[lockKiosk]')
    sendCommandPost('cmd=lockKiosk')
}

def unlockKiosk() {
    logger('D', '[unlockKiosk]')
    sendCommandPost('cmd=unlockKiosk')
}

def startScreensaver() {
    logger('D', '[startScreensaver]')
    sendCommandPost('cmd=startScreensaver')
}

def stopScreensaver() {
    logger('D', '[stopScreensaver]')
    sendCommandPost('cmd=stopScreensaver')
}

def loadURL(url) {
    logger('D', {"[loadURL]: ${url}"})
    sendCommandPost("cmd=loadURL&url=${urlEncode(url)}")
}

def loadStartURL() {
    logger('D', '[loadStartURL]')
    sendCommandPost(CMD_LOAD_START_URL)
}

/** ImageCapture: take() is the capability's command. */
def take() {
    logger('D', '[take]')
    getCamshot()
}

def getCamshot() {
    logger('D', '[getCamshot]')
    if (!checkServerDefined()) return
    captureImage('cmd=getCamshot')
}

def getScreenshot() {
    logger('D', '[getScreenshot]')
    if (!checkServerDefined()) return
    captureImage('cmd=getScreenshot')
}

private captureImage(cmd) {
    try {
        def params = [
            uri: fkbUrl(serverIP, serverPort, serverPassword, cmd, false),
            contentType: 'application/octet-stream'
        ]
        httpGet(params) { response ->
            if (isResponseOk(response)) {
                def respContentType = response.getContentType()
                if (!respContentType?.startsWith('image/')) {
                    logger('W', {"Expected image response but received '${respContentType}'. Check camera permissions and availability on the device."})
                    return
                }
                def imageType = respContentType.contains('png') ? 'png' : 'jpeg'
                def rawBytes = response.data.bytes
                def base64Image = rawBytes.encodeBase64().toString()
                sendEvent(name: "image", value: "<img src='data:image/${imageType};base64,${base64Image}' width='100%'>")
                logger('I', "Image captured and stored in 'image' attribute")
                if (settings.imageClearInterval > 0) {
                    runIn(settings.imageClearInterval.toInteger(), 'clearImage')
                }
            } else {
                logger('E', {"Image capture failed: ${response.status}"})
            }
        }
    } catch (Exception e) {
        logger('E', {"Error capturing image: ${e.message}"})
    }
}

def clearImage() {
    logger('I', '[clearImage]')
    sendEvent(name: 'image', value: 'none')
}

def speak(text, volume=-1, voice='') {
    logger('D', '[speak]')
    if (text == null) {
        logger('W', 'speak: no text given')
        return
    }
    text = "${text}"
    logger('T', {"text,volume,voice:${groovy.xml.XmlUtil.escapeXml(text)},${volume},${voice}"})
    logger('T', {"settings.ttsEngine: ${settings.ttsEngine}"})
    text = text.replace('{', '<').replace('}', '/>')
    Integer speakVolume = parseSpeakVolume(volume)
    switch ("${settings.ttsEngine}") {
        case 'null':
        case '0':
            if (text == '!') {
                stopSound()
            } else {
                logger('D', 'Using the Hubitat TTS Engine')
                logger('T', {"Updated text:${groovy.xml.XmlUtil.escapeXml(text)}"})
                if (text.startsWith('!')) {
                    text = text.substring(1)
                }
                def sound = textToSpeech(text, voice ?: '')
                logger('T', {"sound.uri: ${sound.uri}"})
                logger('T', {"sound.duration: ${sound.duration}"})
                playAfterVolume(speakVolume, playSoundCmd(sound.uri))
            }
            break
        case '1':
            if (text == '!') {
                sendCommandPost('cmd=stopTextToSpeech')
            } else {
                logger('D', 'Using the Fully Kiosk Browser TTS Engine')
                def queue = text.startsWith('!') ? '0' : '1'
                if (text.startsWith('!')) {
                    text = text.substring(1)
                }
                logger('T', {"Updated text:${groovy.xml.XmlUtil.escapeXml(text)}"})
                String locale = ttsLanguage ? "&locale=${urlEncode(ttsLanguage)}" : ''
                playAfterVolume(speakVolume, "cmd=textToSpeech${locale}&text=${urlEncode(text)}&queue=${queue}&engine=${urlEncode(voice)}")
            }
            break
        default:
            break
    }
}

/** Sends playCmd — after setting the volume, when one was given, and only once the tablet has answered it. */
private void playAfterVolume(Integer volume, String playCmd) {
    if (volume != null) {
        sendVolume(volume, playCmd)
    } else {
        sendCommandPost(playCmd)
    }
}

def setVolume(volumeLevel) {
    logger('D', '[setVolume]')
    sendVolume(volumeLevel)
}

/** Sets the volume of the configured stream. nextCmd, if any, is sent after the tablet answers (see sendCommandPost). */
private void sendVolume(volumeLevel, String nextCmd = null) {
    logger('T', {"volumeLevel:${volumeLevel}"})
    logger('T', {"volumeStream:${volumeStream}"})
    Integer vl = toIntOrNull(volumeLevel)
    Integer vs = toIntOrNull(volumeStream)

    if (vl != null && vl >= 0 && vl <= 100 && vs != null && vs >= 1 && vs <= 10) {
        sendCommandPost("cmd=setAudioVolume&level=${vl}&stream=${vs}", nextCmd)
        sendEvent([name:'volume', value:vl])
        state.remove('mute')
        sendEvent([name:'mute', value:'unmuted'])
    } else {
        logger('D', 'volumeLevel or volumeStream out of range')
        if (nextCmd) sendCommandPost(nextCmd)
    }
}

def volumeUp() {
    logger('D', '[volumeUp]')
    stepVolume(10)
}

def volumeDown() {
    logger('D', '[volumeDown]')
    stepVolume(-10)
}

private void stepVolume(int delta) {
    // While muted, state.mute holds the volume from before mute(): stepping from it also unmutes.
    Integer target = volumeTarget(state.mute, device.currentValue('volume'), delta)
    if (target == null) {
        logger('D', 'No volume currently set')
        return
    }
    sendVolume(target)
}

def mute() {
    logger('D', '[mute]')
    if (state.mute != null) {
        logger('D', 'Already muted')
        return
    }
    // Read before zeroing: read after, the volume could already be 0, and unmute would then restore 100%.
    Integer previous = volumeToRestore(null, device.currentValue('volume'))
    sendVolume(0)
    state.mute = previous
    sendEvent([name:'mute', value:'muted'])
    logger('T', {"Previous volume saved to state.mute:${state.mute}"})
}

def unmute() {
    logger('D', '[unmute]')
    if (state.mute != null) {
        sendVolume(state.mute)
    } else {
        logger('D', 'Not muted')
    }
}

def refresh() {
    logger('D', '[refresh]')
    if (!checkServerDefined()) return
    asynchttpPost('refreshCallback', getRequestParams('cmd=deviceInfo'), null)
}

/** Records a poll failure, logs it, and sets device offline after POLL_FAILURE_THRESHOLD consecutive failures. */
private recordPollFailure(String message) {
    state.pollFailures = (state.pollFailures ?: 0) + 1
    logger('W', {"${message} (failure ${state.pollFailures}/${POLL_FAILURE_THRESHOLD})"})
    if (state.pollFailures >= POLL_FAILURE_THRESHOLD) {
        if (device.currentValue('networkStatus') != 'offline') {
            sendEvent([name:'networkStatus', value:'offline'])
            logger('E', "Device is offline after ${POLL_FAILURE_THRESHOLD} consecutive poll failures.")
        }
        state.pollFailures = 0
    }
}

def refreshCallback(response, data) {
    logger('D', '[refreshCallback]')
    def resp = (response instanceof String && data != null) ? data : response
    logger('T', {"response.status: ${resp?.status ?: resp?.getStatus()}"})
    logger('T', {"response: ${response}"})
    if (isResponseOk(response, data)) {
        def json = safeGetResponseJson(resp)
        if (json == null || !(json instanceof Map)) {
            recordPollFailure('Response had no valid JSON')
            return
        }
        if (state.pollFailures > 0) {
            logger('I', 'Device responded successfully after previous failures. Resetting poll failure count and updating network status to online.')
        }
        state.pollFailures = 0
        if (device.currentValue('networkStatus') != 'online') {
            sendEvent([name:'networkStatus', value:'online'])
            logger('I', 'Device is online.')
        }
        logger('T', {"response.json: ${json}"})

        if (settings.reportBattery) {
            handleBatteryLevel(json.batteryLevel)
        }
        // Each field is used only when the tablet reports it: some Fully Kiosk versions leave out e.g.
        // motionDetected and audioVolume (1.60 does), and a missing field must not read as 'inactive' or null.
        if (settings.reportMotion && json.motionDetected != null) {
            def motionValue = json.motionDetected ? 'active' : 'inactive'
            if (motionValue != device.currentValue('motion')) {
                logger('I', {"Motion: ${motionValue}"})
                sendEvent([name:'motion', value:motionValue])
            }
        }

        if (settings.reportCameraMotion && json.motionDetected != null) {
            def cameraMotionValue = json.motionDetected ? 'active' : 'inactive'
            if (cameraMotionValue != device.currentValue('cameraMotion')) {
                logger('I', {"Camera Motion: ${cameraMotionValue}"})
                sendEvent([name:'cameraMotion', value:cameraMotionValue])
            }
        }
        if (settings.reportAcceleration && json.movementDetected != null) {
            def accelerationValue = json.movementDetected ? 'active' : 'inactive'
            if (accelerationValue != device.currentValue('acceleration')) {
                logger('I', {"Acceleration: ${accelerationValue}"})
                sendEvent([name:'acceleration', value:accelerationValue])
            }
        }
        if (settings.reportVolume && json.audioVolume != null) {
            if (json.audioVolume != device.currentValue('volume')) {
                logger('I', {"Volume: ${json.audioVolume}"})
                sendEvent([name:'volume', value:json.audioVolume])
            }
        }
        if (settings.reportSwitch && json.screenOn != null) {
            def switchValue = (json.screenOn == true) ? 'on' : 'off'
            if (switchValue != device.currentValue('switch')) {
                logger('I', {"Switch: ${switchValue}"})
                sendEvent([name:'switch', value:switchValue])
            }
        }
        if (settings.reportBrightness && json.screenBrightness != null) {
            Integer level = brightnessToLevel(json.screenBrightness)
            if (level != device.currentValue('level')) {
                logger('I', {"Brightness Level: ${level}% (tablet: ${json.screenBrightness}/255)"})
                sendEvent([name:'level', value:level, unit:'%'])
            }
        }
        if (settings.reportPageUrl && json.currentPage != null) {
            if (json.currentPage != device.currentValue('currentPageUrl')) {
                logger('I', {"Current Page URL: ${json.currentPage}"})
                sendEvent([name:'currentPageUrl', value:json.currentPage])
            }
        }
    } else {
        def fkbMessage = safeGetResponseJson(resp)?.statustext ?: (resp?.status ?: resp?.getStatus())
        recordPollFailure("Invalid response: ${fkbMessage}")
    }
}

def handleBatteryLevel(batteryLevel) {
    Integer batteryValue = toIntOrNull(batteryLevel)
    if (batteryValue == null) return
    if (batteryValue != device.currentValue('battery')) {
        logger('I', {"Battery Level: ${batteryValue}%"})
        sendEvent([name:'battery', value:batteryValue])
    }
}

def both() {
    logger('D', '[both]')
    sirenStart('both')
}

def strobe() {
    logger('D', '[strobe]')
    sirenStart('strobe')
}

def siren() {
    logger('D', '[siren]')
    sirenStart('siren')
}

def sirenStart(eventValue) {
    logger('I', '[sirenStart]')
    if (!sirenFile) {
        logger('W', 'Siren File URL not set in preferences')
        return
    }
    logger('T', {"sirenFile:${sirenFile}"})
    logger('T', {"sirenVolume:${sirenVolume}"})
    logger('T', {"eventValue:${eventValue}"})
    if (sirenVolume && sirenFile && eventValue) {
        state.siren = volumeToRestore(state.mute, device.currentValue('volume'))
        logger('T', {"Previous volume saved to state.siren:${state.siren}"})
        // One volume command, then the sound once the tablet has answered it. unmute() + setVolume() used to send
        // two volume commands at once, and when they arrived swapped the siren played at the restored volume.
        sendVolume(sirenVolume, playSoundCmd(sirenFile, true))
        sendEvent([name:'alarm', value:eventValue])
        int clearAfter = safeToInt(settings.clearAlarmInterval, 0)
        if (clearAfter > 0) {
            runIn(clearAfter, 'sirenStop')
        }
    } else {
        logger('D', 'sirenFile,sirenVolume or eventValue not set')
    }
}

def sirenStop() {
    logger('I', '[sirenStop]')
    if (state.siren != null) {
        sendVolume(state.siren)
        state.remove('siren')
    }
    sendEvent([name:'alarm', value:'off'])
    stopSound()
}

def playSound(soundFile) {
    logger('D', {"[playSound] soundFile:${soundFile}"})
    if (!soundFile) {
        logger('W', 'playSound: no sound file URL given')
        return
    }
    sendCommandPost(playSoundCmd(soundFile))
}

def stopSound() {
    logger('D', '[stopSound]')
    sendCommandPost('cmd=stopSound')
}

def setBooleanSetting(key, value) {
    logger('D', {"[setBooleanSetting] key,value: ${key},${value}"} )
    sendCommandPost("cmd=setBooleanSetting&key=${key}&value=${value}")
}

def setStringSetting(key, value) {
    logger('D', {"[setStringSetting] key,value: ${key},${value}"})
    sendStringSetting(key, value)
}

private void sendStringSetting(key, value, String nextCmd = null) {
    sendCommandPost("cmd=setStringSetting&key=${key}&value=${urlEncode(value)}", nextCmd)
}

// *** [ Communication Methods ] **********************************************
def updateDeviceData() {
    logger('D', '[updateDeviceData]')
    if (!checkServerDefined()) return
    asynchttpGet('updateDeviceDataCallback', getRequestParams('cmd=deviceInfo'))
}

def updateDeviceDataCallback(response, data) {
    logger('D', '[updateDeviceDataCallback]')
    def resp = (response instanceof String && data != null) ? data : response
    logger('T', {"response status,data: ${resp?.status ?: resp?.getStatus()},${response instanceof String ? response : data}"})
    if (isResponseOk(response, data)) {
        def json = safeGetResponseJson(resp)
        if (json == null) {
            logger('W', 'Device info response had no valid JSON')
            return
        }
        logger('D', {"response.json: ${json}"})
        ['appVersionName', 'deviceManufacturer', 'androidVersion', 'deviceModel'].each { key ->
            if (json[key] != null) device.updateDataValue(key, "${json[key]}")
        }
    } else {
        def statustext = (resp?.json instanceof Map) ? resp.json.statustext : null
        if (statustext?.toString()?.toLowerCase()?.contains('please login')) {
            logger('E', {"FKB returned '${statustext}'. The server password in preferences may be wrong."})
        } else {
            logger('E', {"Invalid response: ${statustext ?: (resp?.status ?: resp?.getStatus())}"})
        }
    }
}

def checkServerDefined() {
    def infoSet = serverIP && serverPort && serverPassword
    if (!infoSet) {
        logger('W', 'Server (Device) IP, Port or Password not set. Command not sent.')
    }
    return infoSet
}

/**
 * Commands are asynchronous, and the tablet may handle two of them in either order. When one must follow another
 * (play a sound after setting its volume, reload the page after storing the script), pass it as nextCmd: it is
 * sent from this command's callback, whatever the answer — so e.g. the siren still sounds if the volume failed.
 */
def sendCommandPost(cmdDetails='', String nextCmd=null) {
    logger('D', '[sendCommandPost]')
    if (!checkServerDefined()) return
    logger('T', {"cmdDetails:${cmdDetails}"})
    asynchttpPost('sendCommandCallback', getRequestParams(cmdDetails), nextCmd ? [nextCmd: nextCmd] : null)
}

def sendCommandCallback(response, data) {
    logger('D', '[sendCommandCallback]')
    def resp = (response instanceof String && data != null) ? data : response
    logger('T', {"response.status: ${resp?.status ?: resp?.getStatus()}"})
    if (isResponseOk(response, data)) {
        logger('D', {"response.data: ${resp.data}"})
    } else {
        logger('E', {"Invalid response: ${resp?.status ?: resp?.getStatus()}"})
    }
    if (data instanceof Map && data.nextCmd) {
        sendCommandPost(data.nextCmd)
    }
}

def getRequestParams(cmdDetails='') {
    def params = [
        uri: fkbUrl(serverIP, serverPort, serverPassword, cmdDetails, true),
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    logger('T', {"Request uri: ${maskPassword(params.uri)}"})
    return params
}

/** Returns response JSON map or null; never throws (e.g. when response has no JSON or getter throws). */
private safeGetResponseJson(resp) {
    if (resp == null) return null
    try {
        def j = resp.json
        return (j instanceof Map) ? j : null
    } catch (Exception e) {
        return null
    }
}

/** Returns true if HTTP response is 200 and (for JSON) FKB did not return status/Error. Safe for binary responses (e.g. image) and async (response/data) argument order. */
def isResponseOk(response, data = null) {
    def resp = (response instanceof String && data != null) ? data : response
    def httpStatus = (resp?.respondsTo('getStatus') ? resp.getStatus() : null) ?: resp?.status
    if (httpStatus != 200) return false
    // FKB JSON may be in .json (async) or .data (sync); sync HttpResponseDecorator has no .json. Only read .json if present.
    def payload = null
    if (resp?.data instanceof Map) payload = resp.data
    else if (resp?.hasProperty('json') && resp.json instanceof Map) payload = resp.json
    if (payload != null && payload.status == 'Error') return false
    if (payload != null && payload.status != null && payload.status != 'OK') return false
    return true
}

// *** [ Pure Helpers ] — no hub state; covered by FullyKioskBrowserSpec ********

/** URL of a Fully Kiosk REST command. The password is URL-encoded: raw, a '&', '#', '+' or space broke every command. */
String fkbUrl(ip, port, password, String cmdDetails, boolean json = true) {
    return "http://${ip}:${port}/?${json ? 'type=json&' : ''}password=${urlEncode(password)}&${cmdDetails}"
}

String urlEncode(value) {
    return java.net.URLEncoder.encode(value == null ? '' : "${value}", 'UTF-8')
}

String maskPassword(String url) {
    return url?.replaceAll(/password=[^&]*/, 'password=***')
}

String playSoundCmd(url, boolean loop = false) {
    return "cmd=playSound${loop ? '&loop=true' : ''}&url=${urlEncode(url)}"
}

/** An IPv4 address as the 8-hex-digit DNI the hub routes reports by, or null when ip isn't an IPv4 address. */
String ipToHex(ip) {
    String[] parts = "${ip ?: ''}".trim().split('\\.', -1)
    if (parts.length != 4 || !parts.every { it ==~ /\d{1,3}/ && it.toInteger() <= 255 }) return null
    return parts.collect { String.format('%02X', it.toInteger()) }.join()
}

/**
 * Quartz cron that polls every `minutes`, on second :00 — or null when no cron keeps that pace, and the caller
 * chains runIn instead. 1.7.x always built "0 0/<minutes> * * * ?": Quartz rejects an increment of 60 or more,
 * and one that doesn't divide 60 restarts each hour (45 polled at :00 and :45).
 */
String pollCron(minutes) {
    int m = safeToInt(minutes, 0)
    if (m <= 0) return null
    if (m < 60) return (60 % m == 0) ? "0 0/${m} * * * ?" : null
    if (m % 60 != 0) return null
    int hours = m.intdiv(60)
    if (hours == 24) return '0 0 0 * * ?'
    return (hours < 24 && 24 % hours == 0) ? "0 0 0/${hours} * * ?" : null
}

/** Volume to step from: the one mute() saved while muted, else the current one. null when neither is known. */
Integer volumeTarget(saved, current, int delta) {
    Integer base = firstVolume(saved, current)
    return base == null ? null : clampInt(base + delta, 0, 100)
}

/** Volume to put back after the siren or unmute: saved, else current, else 100. A 0 stays 0 (1.7.x made it 100). */
Integer volumeToRestore(saved, current) {
    Integer base = firstVolume(saved, current)
    return base == null ? 100 : clampInt(base, 0, 100)
}

private Integer firstVolume(saved, current) {
    Integer v = toIntOrNull(saved)
    return v != null ? v : toIntOrNull(current)
}

/** speak()'s optional volume: null when not given (-1 is the capability's default, or null/blank), else 0-100. */
Integer parseSpeakVolume(volume) {
    Integer v = toIntOrNull(volume)
    return (v == null || v < 0) ? null : clampInt(v, 0, 100)
}

/** SwitchLevel's 0-100% as the tablet's 0-255 screen brightness. */
Integer levelToBrightness(level) {
    Integer lvl = toIntOrNull(level)
    return lvl == null ? null : rescale(clampInt(lvl, 0, 100), 255, 100)
}

/** The tablet's 0-255 screen brightness as 0-100%. */
Integer brightnessToLevel(brightness) {
    Integer b = toIntOrNull(brightness)
    return b == null ? null : rescale(clampInt(b, 0, 255), 100, 255)
}

private int rescale(int value, int to, int from) {
    return new BigDecimal(value * to).divide(new BigDecimal(from), 0, RoundingMode.HALF_UP).intValue()
}

int clampInt(int value, int min, int max) {
    return Math.max(min, Math.min(max, value))
}

/** Lenient number parsing for settings, attributes and command arguments: '55.0' -> 55, 42.6 -> 43, junk -> null. */
Integer toIntOrNull(value) {
    if (value == null) return null
    String s = "${value}".trim()
    if (!s) return null
    try {
        return new BigDecimal(s).setScale(0, RoundingMode.HALF_UP).intValueExact()
    } catch (Exception ignored) {
        return null
    }
}

// *** [ Log Methods ] ****************************************************
private void logger(level, message) {
    int configuredLevel = getCachedLoggingLevel()
    switch (level) {
        case 'E': if (configuredLevel >= 1) { log.error(getLogMessage(message)) }; break
        case 'W': if (configuredLevel >= 2) { log.warn(getLogMessage(message)) }; break
        case 'I': if (configuredLevel >= 3) { log.info(getLogMessage(message)) }; break
        case 'D': if (configuredLevel >= 4) { log.debug(getLogMessage(message)) }; break
        case 'T': if (configuredLevel >= 5) { log.trace(getLogMessage(message)) }; break
    }
}

private String getLogMessage(message) {
    def text = (message instanceof Closure) ? message() : message
    return "${device.displayName}: ${text}"
}

private void updateCachedLoggingLevel() {
    cachedLoggingLevel = safeToInt(settings.loggingLevel, 3)
}

private int getCachedLoggingLevel() {
    if (cachedLoggingLevel >= 0) return cachedLoggingLevel
     cachedLoggingLevel = (settings.loggingLevel as String)?.toInteger() ?: 3
    return cachedLoggingLevel
}

Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}