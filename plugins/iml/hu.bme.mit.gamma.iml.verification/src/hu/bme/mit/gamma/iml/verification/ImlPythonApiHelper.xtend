package hu.bme.mit.gamma.iml.verification

import java.util.Scanner
import java.util.logging.Logger

class ImlPythonApiHelper {
	// Singleton
	public static final ImlPythonApiHelper INSTANCE = new ImlPythonApiHelper
	protected new() {}
	//
	protected final Logger logger = Logger.getLogger("GammaLogger")
	//
	
	def void killImandraInstances() {
		var Scanner resultReader = null
		var Process process = null
		var instanceIds = newArrayList
		val commandPrefix = #["imandra-cli", "core", "instances"] // TODO Return if not found
		try {
			val command = commandPrefix + #["list"]
			//Instances:
			//
			// [2024-11-22T23:54:41-00:00] [imandra-server] 557721e1-2fd2-4806-b84b-bc618a07627d
			// [2024-11-22T23:57:07-00:00] [imandra-server] 87a300dd-51c9-484c-ae0c-31a5a1629732
			process = Runtime.getRuntime().exec(command)
			
			resultReader = new Scanner(process.inputReader)
			if (resultReader.hasNextLine) {
				resultReader.nextLine
				if (resultReader.hasNextLine) {
					resultReader.nextLine
				}
			}
			
			logger.info("Looking for alive Imandra instances...")
			while (resultReader.hasNextLine) {
				val line = resultReader.nextLine
				val split = line.split(" ")
				val instanceId = split.last
				logger.info("Found: " + instanceId)
				instanceIds += instanceId
			}
		} catch (Exception e) {
		} finally {
			resultReader?.close
			process?.destroy
		}
		for (instanceId : instanceIds) {
			try {
				val command = commandPrefix + #["kill", "--id", instanceId]
				process = Runtime.getRuntime().exec(command)
				resultReader = new Scanner(process.inputReader)
				while (resultReader.hasNextLine) {
					val line = resultReader.nextLine
					logger.info(line)
				}
			} catch (Exception e) {
			} finally {
				resultReader?.close
				process?.destroy
			}
		}
	}
	
}