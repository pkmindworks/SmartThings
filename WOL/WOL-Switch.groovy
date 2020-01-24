/**
 *  WOL Switch
 *  Copyright 2016 Sumit Garg
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
	definition (name: "WOL Switch", namespace: "sumitkgarg", author: "Sumit Garg") {
		// capability "Actuator"
		// capability "Sensor"
		capability "Switch"
		capability "Refresh"
	}

	preferences {
		input "macAddress", "string", title: "MAC Address", description: "Enter the MAC address of the computer", required: true, displayDuringSetup: true
		input "ipAddress", "string", title: "IP Address", description: "Enter the IP address of the computer", required: true, displayDuringSetup: true
		input "HEXPort", "string", title: "HEXPort", description: "Enter the Port for status in HEX", required: true, displayDuringSetup: true
		input "Port", "string", title: "Port", description: "Enter the Port for status", required: true, displayDuringSetup: true
	}

	simulator {
	}

	tiles(scale: 2) {
		standardTile("switch", "device.switch", decoration: "flat", width: 6, height: 4, canChangeIcon: true) {
			state "on", label:'${currentValue}', icon:"st.switches.switch.on", backgroundColor:"#79b821"
			state "off", label:'${currentValue}', icon:"st.switches.switch.off", backgroundColor:"#ffffff"
			state "scanning", label:'SCANNING', icon:"st.switches.switch.off", backgroundColor:"#ffa81e"
		}

		standardTile("turnon", "device.switch", decoration: "flat", width: 2, height: 2) {
			state "on", label:'On', action:"switch.on", icon:"st.switches.switch.on", backgroundColor: "#aaefad" //"#79b821" //"#dff0d8"
			state "off", label:'On', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#aaefad"
		}

		standardTile("turnoff", "device.switch", decoration: "flat", width: 2, height: 2) {
			state "on", label:'Off', action:"switch.off", icon: "st.switches.switch.off", backgroundColor: "#efaaaa" //"#f2dede"
			state "off", label:'Off', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#efaaaa"
		}

		standardTile("refresh", "device.switch", decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh" //, backgroundColor: "#efd2aa"
		}

		main "switch"
		details (["switch", "turnon", "turnoff", "refresh"])
	}
}

def parse(description) {
	def msg = parseLanMessage(description)
	def ok = msg.status == 200
	def switchResponse = msg.port == "${HEXPort}"

	// log.debug "${description}"

	if (switchResponse) {
		setSwitchValue(ok ? "on" : "off")
	}
}

// Get the state of the machine
def machineGET() {
	sendHubCommand(new physicalgraph.device.HubAction([
		method: "GET",
		path: "/",
		headers: [
		HOST: "${ipAddress}:${Port}",
	]]))

	log.debug "Sent HTTP request to PC - ${ipAddress}:${Port}"
}

// Using a third party app on the windows server
// to make the computer go to sleep by making a GET request
def machineSleepGET() {
	sendHubCommand(new physicalgraph.device.HubAction([
		method: "GET",
		path: "/shutdown?auth=Shutd0wN",
		headers: [
		HOST: "${ipAddress}:${Port}",
	]]))

	log.debug "Sent HTTP SLEEP request to PC - http://${ipAddress}:${Port}/shutdown?auth=Shutd0wN"
}

def sendWOL() {
	sendHubCommand(new physicalgraph.device.HubAction(
		"wake on lan ${macAddress.replaceAll(':','').replaceAll(' ','').toLowerCase()}",
		physicalgraph.device.Protocol.LAN,
		null,
		[:]
	))

	log.debug "Sent WOL request to PC - ${ipAddress}"
}

def on() {
	initializeCron()
	log.debug "Executing 'on'"

	setScanningMode()
	sendWOL()
	machineGET() // in case it is already on - for more immediate feedback
	runIn(7, machineGET) // in case it is still waking up
	runIn(12, timeout)
}

def off() {
	initializeCron()
	log.debug "Executing 'off'"

	// setScanningMode()
	machineSleepGET()
	setSwitchValue("off")
}

def refresh() {
	initializeCron()
	if (getSwitchValue() != "scanning") {
		log.debug "Executing 'refresh'"

		setScanningMode()    
		machineGET()

		runIn(5, timeout)
	}
}

def initializeCron() {
	if (state.initialized != "true") {
		state.initialized = "true"
		// refresh every 10 minutes.
		def cronExpression = "0 0/10 * 1/1 * ? *"
		log.debug "Initializing refresh schedule based on cron expression - '${cronExpression}'"
		schedule(cronExpression, refresh)
	}
}

def timeout() {
	if (getSwitchValue() == "scanning") {
		log.debug "Executing 'timeout' for switch"
		setSwitchValue("off")
	}
}

def getSwitchValue() {
	return state.switch
}

def setScanningMode(){
	setSwitchValue("scanning")
}

def setSwitchValue(val) {
	if (state.switch != val) {
		state.switch = val
		sendEvent(name: "switch", value: val)
		log.debug "Set Switch value to ${val}"
	}
}

def setDeviceNetworkId(dni) {
	log.debug "Changing DNI to ${dni}"
	device.setDeviceNetworkId(dni)
}

def getDeviceNetworkId(port) {
	return "${convertIPtoHex(ipAddress)}:${convertPortToHex(port)}"
}

private String convertIPtoHex(ipAddress) {
	return ipAddress.tokenize('.').collect{String.format('%02x', it.toInteger())}.join()
}

private String convertPortToHex(port) {
	return port.toString().format('%04x', port.toInteger())
}

// c0a8000e:0016 - 192.168.0.14:22
// c0a80010:0050 - 192.168.0.16:80
// c0a80010:7e90 - 192.168.0.16:32400
// c0a80010:1f40 - 192.168.0.16:8000
// C0A85634:0050 - 192.168.86.52:80
// change the dni in My Devices for this device if IP address changs
