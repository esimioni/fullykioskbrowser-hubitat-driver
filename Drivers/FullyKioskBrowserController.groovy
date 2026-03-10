// Fully Kiosk Browser Driver 1.7.1
// Github: https://github.com/esimioni/fullykioskbrowser-hubitat-driver

// Fork of Original Driver by Gavin Campbell 1.4.1:
// Github: https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy
// Original Support: https://community.hubitat.com/t/release-fully-kiosk-browser-controller/12223
/*
[Change Log]
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

metadata {
    definition(name: 'Fully Kiosk Browser Controller', namespace: 'esimioni', author: 'Eduardo Simioni', importUrl: 'https://raw.githubusercontent.com/esimioni/fullykioskbrowser-hubitat-driver/refs/heads/master/Drivers/FullyKioskBrowserController.groovy') {
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
        command 'setScreenBrightness', ['Number']
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
                                     [name:'Value*:', type:'ENUM', constraints:['true', 'false'], desciption:'The setting to be applied.']]
        command 'setStringSetting', [[name:'Key*', type:'STRING', description:'The key value associated with the setting to be updated.'],
                                     [name:'Value*:', type:'STRING', desciption:'The setting to be applied.']]
    }
    preferences {
        input(name:'serverIP', type:'string', title:'Server IP Address', description:'Device IP', defaultValue:'', required:true)
        input(name:'serverPort', type:'string', title:'Server Port', defaultValue:'2323', required:true)
        input(name:'serverPassword', type:'string', title:'Server Password', defaultValue:'', required:true)
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
    unschedule()

    def mac = getMACFromIP("${serverIP}")
    if (mac) {
        logger('I', {"MAC address found. Updating deviceNetworkId: ${mac}"})
        device.deviceNetworkId = mac
    } else if (settings.serverIP) {
        logger('I', {"MAC address not found. Setting deviceNetworkId to ip address: ${settings.serverIP}"})
        device.deviceNetworkId = settings.serverIP
    } else {
        device.deviceNetworkId = "TBD-${device.id}"
        logger('W', {'Server IP not set. DNI temporarily set to "TBD-[deviceId]". Please set Server IP in preferences.'})
    }

    if (settings.pollInterval > 0) {
        state.pollFailures = 0
        sendEvent([name:'networkStatus', value:'online'])
        schedule("0 0/${pollInterval} * * * ?", 'refresh')
        logger('I', {"Polling scheduled every ${pollInterval} minutes."})
    } else {
        unschedule('refresh')
        state.pollFailures = 0
        sendEvent([name:'networkStatus', value:'unknown'])
        logger('I', {'Polling disabled. Network status set to unknown.'})
    }
    updateDeviceData()
    configure()
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
        jsCode += "fully.bind('onBatteryLevelChanged','sendAttributeValue(\\'battery\\',\\'\\');');\n"
        // Also report battery every 15 minutes as a fallback
        jsCode += "setInterval(function(){sendAttributeValue('battery','');}, 900000);\n"
    }

    setStringSetting('injectJsCode', jsCode)
    setBooleanSetting('motionDetection', (settings.reportMotion == true || settings.reportCameraMotion == true))
    setBooleanSetting('movementDetection', settings.reportAcceleration == true)

    // Explicitly disable camera motion if not requested to save CPU
    if (!settings.reportCameraMotion) {
        setBooleanSetting('motionDetection', settings.reportMotion == true)
    }

    loadStartURL()
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
    logger('D', {"[motion] value: ${value}"})
    sendEvent([name:'motion', value:value])
    if (value == 'active') {
        runIn(settings.motionTimeout.toInteger(), 'motionInactive')
    }
}

def motionInactive() {
    sendEvent([name:'motion', value:'inactive'])
}

def cameraMotion(value) {
    logger('D', {"[cameraMotion]: ${value}"})
    sendEvent([name:'cameraMotion', value:value])
    if (value == 'active') {
        runIn(settings.motionTimeout.toInteger(), 'cameraMotionInactive')
    }
}

def cameraMotionInactive() {
    sendEvent([name:'cameraMotion', value:'inactive'])
}

def acceleration(value) {
    logger('D', {"[acceleration]: ${value}"})
    sendEvent([name:'acceleration', value:value])
    if (value == 'active') {
        runIn(settings.motionTimeout.toInteger(), 'accelerationInactive')
    }
}

def accelerationInactive() {
    sendEvent([name:'acceleration', value:'inactive'])
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

def setLevel(level) {
    logger('D', '[setLevel]')
    setScreenBrightness(level)
    sendEvent([name:'level', value:level])
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
    sendCommandPost("cmd=playSound&url=${java.net.URLEncoder.encode(toneFile, 'UTF-8')}")
}

def launchAppPackage(appPackage) {
    logger('D', '[launchAppPackage]')
    sendCommandPost("cmd=startApplication&package=${java.net.URLEncoder.encode(appPackage, 'UTF-8')}")
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

    def cmd = "cmd=setOverlayMessage&text=${java.net.URLEncoder.encode(text, 'UTF-8')}"
    sendCommandPost(cmd)

    if (duration && duration.toInteger() > 0) {
        runIn(duration.toInteger(), 'clearOverlayMessage')
    }
}

def clearOverlayMessage() {
    logger('D', '[clearOverlayMessage]')
    sendCommandPost('cmd=setOverlayMessage&text=')
}

def showToast(text) {
    logger('D', {"[showToast] text:${text}"})
    sendCommandPost("cmd=showToast&text=${java.net.URLEncoder.encode(text, 'UTF-8')}")
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
    sendCommandPost("cmd=loadURL&url=${java.net.URLEncoder.encode(url, 'UTF-8')}")
}

def loadStartURL() {
    logger('D', '[loadStartURL]')
    sendCommandPost('cmd=loadStartURL')
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
            uri: "http://${serverIP}:${serverPort}/?password=${serverPassword}&${cmd}",
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
    logger('T', {"text,volume,voice:${groovy.xml.XmlUtil.escapeXml(text)},${volume},${voice}"})
    logger('T', {"settings.ttsEngine: ${settings.ttsEngine}"})
    text = text.replace('{', '<').replace('}', '/>')
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
                def sound = textToSpeech(text, voice)
                logger('T', {"sound.uri: ${sound.uri}"})
                logger('T', {"sound.duration: ${sound.duration}"})
                if (volume != -1) setVolume(volume)
                playSound(sound.uri)
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
                sendCommandPost("cmd=textToSpeech${ttsLanguage ? '&locale=' + ttsLanguage : ''}&text=${java.net.URLEncoder.encode(text, 'UTF-8')}&queue=${queue}&engine=${java.net.URLEncoder.encode(voice, 'UTF-8')}")
            }
            break
        default:
            break
    }
}

def setVolume(volumeLevel) {
    logger('D', '[setVolume]')
    logger('T', {"volumeLevel:${volumeLevel}"})
    logger('T', {"volumeStream:${volumeStream}"})
    def vl = volumeLevel.toInteger()
    def vs = volumeStream.toInteger()

    if (vl >= 0 && vl <= 100 && vs >= 1 && vs <= 10) {
        sendCommandPost("cmd=setAudioVolume&level=${vl}&stream=${vs}")
        sendEvent([name:'volume', value:vl])
        state.remove('mute')
        sendEvent([name:'mute', value:'unmuted'])
    } else {
        logger('D', 'volumeLevel or volumeStream out of range')
    }
}

def volumeUp() {
    logger('D', '[volumeUp]')
    def newVolume = state.mute ?: device.currentValue('volume')
    if (newVolume) {
        newVolume = newVolume.toInteger() + 10
        newVolume = Math.min(newVolume, 100)
        setVolume(newVolume)
    } else {
        logger('D', 'No volume currently set')
    }
}

def volumeDown() {
    logger('D', '[volumeDown]')
    def newVolume = state.mute ?: device.currentValue('volume')
    if (newVolume) {
        newVolume = newVolume.toInteger() - 10
        newVolume = Math.max(newVolume, 0)
        setVolume(newVolume)
    } else {
        logger('D', 'No volume currently set')
    }
}

def mute() {
    logger('D', '[mute]')
    if (!state.mute) {
        setVolume(0)
        state.mute = device.currentValue('volume') ?: 100
        sendEvent([name:'mute', value:'muted'])
        logger('T', {"Previous volume saved to state.mute:${state.mute}"})
    } else {
        logger('D', 'Already muted')
    }
}

def unmute() {
    logger('D', '[unmute]')
    if (state.mute) {
        setVolume(state.mute)
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
            logger('E', 'Device is offline after 3 consecutive poll failures.')
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
        if (settings.reportMotion) {
            def motionValue = json.motionDetected ? 'active' : 'inactive'
            if (motionValue != device.currentValue('motion')) {
                logger('I', {"Motion: ${motionValue}"})
                sendEvent([name:'motion', value:motionValue])
            }
        }

        if (settings.reportCameraMotion) {
            def cameraMotionValue = json.motionDetected ? 'active' : 'inactive'
            if (cameraMotionValue != device.currentValue('cameraMotion')) {
                logger('I', {"Camera Motion: ${cameraMotionValue}"})
                sendEvent([name:'cameraMotion', value:cameraMotionValue])
            }
        }
        if (settings.reportAcceleration) {
            def accelerationValue = json.movementDetected ? 'active' : 'inactive'
            if (accelerationValue != device.currentValue('acceleration')) {
                logger('I', {"Acceleration: ${accelerationValue}"})
                sendEvent([name:'acceleration', value:accelerationValue])
            }
        }
        if (settings.reportVolume) {
            if (json.audioVolume != device.currentValue('volume')) {
                logger('I', {"Volume: ${json.audioVolume}"})
                sendEvent([name:'volume', value:json.audioVolume])
            }
        }
        if (settings.reportSwitch) {
            def switchValue = (json.screenOn == true) ? 'on' : 'off'
            if (switchValue != device.currentValue('switch')) {
                logger('I', {"Switch: ${switchValue}"})
                sendEvent([name:'switch', value:switchValue])
            }
        }
        if (settings.reportBrightness && json.screenBrightness != null) {
            if (json.screenBrightness != device.currentValue('level')) {
                logger('I', {"Brightness Level: ${json.screenBrightness}"})
                sendEvent([name:'level', value:json.screenBrightness])
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
    if (batteryLevel == null) return
    def batteryValue = batteryLevel.toInteger()
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
        state.siren = state.mute ?: (device.currentValue('volume') ?: 100)
        logger('T', {"Previous volume saved to state.siren:${state.siren}"})
        unmute()
        setVolume(sirenVolume)
        sendEvent([name:'alarm', value:eventValue])
        sendCommandPost("cmd=playSound&loop=true&url=${java.net.URLEncoder.encode(sirenFile, 'UTF-8')}")
        if (settings.clearAlarmInterval > 0) {
            runIn(settings.clearAlarmInterval.toInteger(), 'sirenStop')
        }
    } else {
        logger('D', 'sirenFile,sirenVolume or eventValue not set')
    }
}

def sirenStop() {
    logger('I', '[sirenStop]')
    if (state.siren) {
        setVolume(state.siren)
        state.remove('siren')
    }
    sendEvent([name:'alarm', value:'off'])
    stopSound()
}

def playSound(soundFile) {
    logger('D', {"[playSound] soundFile:${soundFile}"})
    sendCommandPost("cmd=playSound&url=${java.net.URLEncoder.encode(soundFile, 'UTF-8')}")
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
    sendCommandPost("cmd=setStringSetting&key=${key}&value=${java.net.URLEncoder.encode(value, 'UTF-8')}")
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
        logger({"response.json: ${resp.json}"}, 'debug')
        device.updateDataValue('appVersionName', resp.json.appVersionName)
        device.updateDataValue('deviceManufacturer', resp.json.deviceManufacturer)
        device.updateDataValue('androidVersion', resp.json.androidVersion)
        device.updateDataValue('deviceModel', resp.json.deviceModel)
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

def sendCommandPost(cmdDetails='') {
    logger('D', '[sendCommandPost]')
    if (!checkServerDefined()) return
    logger('T', {"cmdDetails:${cmdDetails}"})
    def postParams = getRequestParams(cmdDetails)
    asynchttpPost('sendCommandCallback', postParams, null)
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
}

def getRequestParams(cmdDetails='') {
    def params = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&${cmdDetails}",
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    logger('T', {"Request params: ${params}"})
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