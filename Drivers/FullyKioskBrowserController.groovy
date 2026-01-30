// Fully Kiosk Browser Driver 1.50
// Github: https://github.com/esimioni/fullykioskbrowser-hubitat-driver

// Fork of Original Driver by Gavin Campbell 1.41:
// Github: https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy
// Original Support: https://community.hubitat.com/t/release-fully-kiosk-browser-controller/12223
/*
[Change Log]
    1.50: Configurable polling interval.
        : States to be updated on Hubitat are now configurable.
        : JS Code injection updated to reduce CPU usage on the tablet and send only configured events.
        : Added cameraMotion event for camera-based motion detection.
        : Added commands to enable/disable touch input (disabling touch will not work on all scenarios)
        : Added commands to lock/unlock kiosk mode.
        : Log handling full refactoring.
        : Implemented closure-based logging for better performance.
        : Code cleanup and optimizations.
    1.41: Fixed speak command. Was broken with Hubitat firmware 2.9.0.
            This will allow it to work with RM and not give an error.
        : Volume will be set if specified (optional), and voice is passed to the engine (optional)
    1.40: Requires Fully Kiosk Browser 1.43.1 or newer.
        : Added auto configuration of webviewMixedContent
            This allows FKB to report in device status to HE from dashboards that use https.
            After upgrading click configure so all the settings get applied.
    1.39: Added attribute "currentPageUrl"
            This attribute is updated with the current page during polling (every minute).
    1.38: Fixed switch reporting.
    1.37: Added State Polling option to allow the driver to poll the device for updates instead of the device reporting in.
            This solves the issue where the start page is SSL. Reporting will not work back to a non SSL endpoint.
            This will gather the screen brightness,screen state and battery levels only.  Motion will not work.
    1.36: Added 'restartApp' command. (Thanks tmleafs)
    1.35: Added 'Battery' capability to track the ... battery.
        : Added 'Switch' and 'SwitchLevel capabilities to turn the screen on/off and adjust the brightness
        : Added 'AccelerationSensor' capability which triggers when tablet is moved.
        : Added 'updateDeviceData' method to record device settings when the preferences is saved.
        : Added 'HealthCheck' capability. Mainly used to help increment Last Activity when device is responding.
        : Removed lastActivity custom attribute. Reduces event log noise.
    1.33: Added 'MotionSensor' capability to monitor motion via the tablet camera.
        : deviceNetworkId will now be set to the MAC of the IP Address to handle callbacks from FKB
        : Fixed setStringSetting method
        : Added 'Configure' capability.
          When you select configure it will configure FKB on the device to send events back to this driver.
          Configure should be run when making configuration changes.
          WARNING: selecting this will overwrite any custom javascript code you currently have setup in fully.
    1.32: If using the FKB TTS Engine, starting text with "!" will cause all messages to be stopped and the new message
          to play. Otherwise the message is added to the queue and will play when others are finished. (Requires FKB v1.38+)
        : Sending a "!" TTS message will stop all currently playing messages to stop. (Requires FKB v1.38+)
    1.31: Updated to use "{ }" instead of "< />" for SSML tags.
    1.30: Added option to select the TTS engine used.
            Hubitat (Amazon): https://docs.aws.amazon.com/polly/latest/dg/supportedtags.html
            Fully Kiosk Browser (Google): https://cloud.google.com/text-to-speech/docs/ssml
    1.24: Added setBooleanSetting,setStringSetting
          Added lastActivity attribute
    1.23: Updated speak() logging to escape XML in logging as speak command can support SSML XML
    1.22: Updated HTTP calls so URL's are encoded properly
    1.21: Fixed the import url to be correct
    1.20: Change speak method to use Hubitat TTS methods. Set voice via Hubitat settings.
    1.09: Changed volumeStream range to be 1-10 (0 doesn't work)
          Made adjustements to setVolume to properly test for volumeStream value
          Added playSound/stopSound commands.
          Added the AudioVolume mute attributes.
          Set default attributes when installed.
*/

metadata {
    definition(name: 'Fully Kiosk Browser Controller', namespace: 'esimioni', author: 'Gavin Campbell / Eduardo Simioni', importUrl: 'https://raw.githubusercontent.com/esimioni/fullykioskbrowser-hubitat-driver/refs/heads/master/Drivers/FullyKioskBrowserController.groovy') {
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
        capability 'HealthCheck'

        attribute 'currentPageUrl', 'String'
        attribute 'cameraMotion', 'String'

        command 'bringFullyToFront'
        command 'launchAppPackage', ['String']
        command 'loadStartURL'
        command 'loadURL', ['String']
        command 'playSound', ['String']
        command 'restartApp'
        command 'screenOn'
        command 'screenOff'
        command 'setScreenBrightness', ['Number']
        command 'startScreensaver'
        command 'stopScreensaver'
        command 'stopSound'
        command 'triggerMotion'
        command 'touchEnable'
        command 'touchDisable'
        command 'lockKiosk'
        command 'unlockKiosk'
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
        input(name:'motionTimeout', type:'number', title:'Motion/Acceleration Timeout', description:'Number of seconds before motion/acceleration is reset to inactive.', defaultValue:30, required:true)
        input(name:'pollInterval', type:'number', title:'Polling Interval', description:'Minutes - Enable this option to force polling of the device states. 0 disables it.', range:[0..1440], defaultValue:5, required:true)
        input(name:'reportMotion', type: 'bool', title: 'Report Motion (Sensors)', defaultValue: false)
        input(name:'reportCameraMotion', type: 'bool', title: 'Report Camera Motion', defaultValue: false)
        input(name:'reportAcceleration', type: 'bool', title: 'Report Acceleration', defaultValue: false)
        input(name:'reportVolume', type: 'bool', title: 'Report Volume', defaultValue: false)
        input(name:'reportSwitch', type: 'bool', title: 'Report Screen On/Off', defaultValue: true)
        input(name:'reportBattery', type: 'bool', title: 'Report Battery Level', defaultValue: true)
        input(name:'reportBrightness', type: 'bool', title: 'Report Screen Brightness', defaultValue: false)
        input(name:'reportPageUrl', type: 'bool', title: 'Report Current Page URL', defaultValue: false)

        input(name:'loggingLevel', type:'enum', title:'Logging Level', description:'Set the level of logging.', options:['none', 'debug', 'trace', 'info', 'warn', 'error'], defaultValue:'debug', required:true)
    }
}

// *** [ Initialization Methods ] *********************************************
def installed() {
    logger('I', '[installed]')
    initialize()
}
def updated() {
    logger('I', '[updated]')
    initialize()
}
def initialize() {
    logger('I', '[initialize]')
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
        logger('W', {'Server IP not set. DNI temporarily set to "TBD-ID". Please set Server IP in preferences.'})
    }

    if (settings.pollInterval > 0) {
        schedule("0 0/${pollInterval} * * * ?", 'refresh')
        logger('I', {"Polling scheduled every ${pollInterval} minutes."})
    } else {
        unschedule('refresh')
        logger('I', {'Polling disabled.'})
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
            sendEvent([name:'checkInterval', value:60])
            logger('E', {"Unknown attribute: ${body.attribute}"})
            break
    }
}
def motion(value) {
    logger('D', {"[motion] value: ${value}"})
    sendEvent([name:'motion', value:value])
    if (value == 'active') {
        runIn(settings.motionTimeout, 'motionInactive')
    }
}
def motionInactive() {
    sendEvent([name:'motion', value:'inactive'])
}
def cameraMotion(value) {
    logger('D', {"[cameraMotion]: ${value}"})
    sendEvent([name:'cameraMotion', value:value])
    if (value == 'active') {
        runIn(settings.motionTimeout, 'cameraMotionInactive')
    }
}
def cameraMotionInactive() {
    sendEvent([name:'cameraMotion', value:'inactive'])
}
def acceleration(value) {
    logger('D', {"[acceleration]: ${value}"})
    sendEvent([name:'acceleration', value:value])
    if (value == 'active') {
        runIn(settings.motionTimeout, 'accelerationInactive')
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
    if (state.siren) {
        setVolume(state.siren)
    }
    state.remove('siren')
    sendEvent([name:'alarm', value:'off'])
    sendCommandPost('cmd=stopSound')
    screenOff()
}
def setLevel(level) {
    logger('D', '[setLevel]')
    setScreenBrightness(level)
    sendEvent([name:'level', value:level])
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
def screenOn() {
    logger('D', '[screenOn]')
    sendCommandPost('cmd=screenOn')
}
def screenOff() {
    logger('D', '[screenOff]')
    sendCommandPost('cmd=screenOff')
}
def setScreenBrightness(value) {
    logger('D', {"[setScreenBrightness]: ${value}"})
    sendCommandPost("cmd=setStringSetting&key=screenBrightness&value=${value}")
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
                sendCommandPost("cmd=textToSpeech&text=${java.net.URLEncoder.encode(text, 'UTF-8')}&queue=${queue}&engine=${java.net.URLEncoder.encode(voice, 'UTF-8')}")
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
    def postParams = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&cmd=deviceInfo",
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    logger('T', {postParams})
    asynchttpPost('refreshCallback', postParams, null)

}
def refreshCallback(response, data) {
    logger('D', '[refreshCallback]')
    logger('T', {"response.status: ${response.status}"})
    if (response?.status == 200) {
        logger('T', {"response.json: ${response.json}"})

        if (settings.reportBattery) {
            handleBatteryLevel(response.json.batteryLevel)
        }
        if (settings.reportMotion) {
            def motionValue = response.json.motionDetected ? 'active' : 'inactive'
            if (motionValue != device.currentValue('motion')) {
                logger('I', {"Motion: ${motionValue}"})
                sendEvent([name:'motion', value:motionValue])
            }
        }

        if (settings.reportCameraMotion) {
            def cameraMotionValue = response.json.motionDetected ? 'active' : 'inactive'
            if (cameraMotionValue != device.currentValue('cameraMotion')) {
                logger('I', {"Camera Motion: ${cameraMotionValue}"})
                sendEvent([name:'cameraMotion', value:cameraMotionValue])
            }
        }
        if (settings.reportAcceleration) {
            def accelerationValue = response.json.movementDetected ? 'active' : 'inactive'
            if (accelerationValue != device.currentValue('acceleration')) {
                logger('I', {"Acceleration: ${accelerationValue}"})
                sendEvent([name:'acceleration', value:accelerationValue])
            }
        }
        if (settings.reportVolume) {
            if (response.json.audioVolume != device.currentValue('volume')) {
                logger('I', {"Volume: ${response.json.audioVolume}"})
                sendEvent([name:'volume', value:response.json.audioVolume])
            }
        }
        if (settings.reportSwitch) {
            def switchValue = (response.json.screenOn == true) ? 'on' : 'off'
            if (switchValue != device.currentValue('switch')) {
                logger('I', {"Switch: ${switchValue}"})
                sendEvent([name:'switch', value:switchValue])
            }
        }
        if (settings.reportBrightness && response.json.screenBrightness != null) {
            if (response.json.screenBrightness != device.currentValue('level')) {
                logger('I', {"Brightness Level: ${response.json.screenBrightness}"})
                sendEvent([name:'level', value:response.json.screenBrightness])
            }
        }
        if (settings.reportPageUrl && response.json.currentPage != null) {
            if (response.json.currentPage != device.currentValue('currentPageUrl')) {
                logger('I', {"Current Page URL: ${response.json.currentPage}"})
                sendEvent([name:'currentPageUrl', value:response.json.currentPage])
            }
        }
    } else {
        logger('E', {"Invalid response: ${response.status}"})
    }
}
def ping() {
    logger('D', '[ping]')
    refresh()
}

def handleBatteryLevel(batteryLevel) {
    if (batteryLevel != device.currentValue('battery')) {
        logger('I', {"Battery Level: ${batteryLevel}%"})
        sendEvent([name:'battery', value:batteryLevel])
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
    logger('D', '[sirenStart]')
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
    } else {
        logger('D', 'sirenFile,sirenVolume or eventValue not set')
    }
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
def updateDeviceData() {
    logger('D', '[updateDeviceData]')
    def httpParams = [
        uri:"http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&cmd=deviceInfo",
        contentType: 'application/json'
    ]
    asynchttpGet('updateDeviceDataCallback', httpParams)
}
def updateDeviceDataCallback(response, data) {
    logger('D', '[updateDeviceDataCallback]')
    logger('T', {"response status,data: ${response.status},${data}"})
    if (response.status == 200) {
        logger({"response.json: ${response.json}"}, 'debug')
        device.updateDataValue('appVersionName', response.json.appVersionName)
        device.updateDataValue('deviceManufacturer', response.json.deviceManufacturer)
        device.updateDataValue('androidVersion', response.json.androidVersion)
        device.updateDataValue('deviceModel', response.json.deviceModel)
        sendEvent([name:'checkInterval', value:60])
    } else {
        logger('E', {"Invalid response: ${response.status}"})
    }
}

// *** [ Communication Methods ] **********************************************
def sendCommandPost(cmdDetails='') {
    logger('D', '[sendCommandPost]')
    logger('T', {"cmdDetails:${cmdDetails}"})
    def postParams = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&${cmdDetails}",
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    logger('T', {postParams})
    asynchttpPost('sendCommandCallback', postParams, null)
}

def sendCommandCallback(response, data) {
    logger('D', '[sendCommandCallback]')
    logger('T', {"response.status: ${response.status}"})
    if (response?.status == 200) {
        logger('D', {"response.data: ${response.data}"})
        sendEvent([name:'checkInterval', value:60])
    } else {
        logger('E', {"Invalid response: ${response.status}"},)
    }
}

private boolean logger(level, message) {
    switch(level) {
        case 'E': log.error(getLogMessage(message)); break
        case 'W': log.warn(getLogMessage(message)); break
        case 'I':
            if (loggingLevel == 'debug' || loggingLevel == 'trace' || loggingLevel == 'info')
                log.info(getLogMessage(message))
            break
        case 'D':
            if (loggingLevel == 'debug' || loggingLevel == 'trace')
                log.debug(getLogMessage(message))
            break
        case 'T':
            if (loggingLevel == 'trace')
                log.trace(getLogMessage(message))
            break
    }
}

private String getLogMessage(message) {
    def text = (message instanceof Closure) ? message() : message
    return "${device.displayName}: ${text}"
}