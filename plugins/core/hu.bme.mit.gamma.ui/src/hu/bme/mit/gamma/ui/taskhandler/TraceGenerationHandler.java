/********************************************************************************
 * Copyright (c) 2018-2023 Contributors to the Gamma project
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package hu.bme.mit.gamma.ui.taskhandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.ecore.resource.Resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hu.bme.mit.gamma.genmodel.model.TraceGeneration;
import hu.bme.mit.gamma.property.util.PropertyUtil;
import hu.bme.mit.gamma.statechart.derivedfeatures.StatechartModelDerivedFeatures;
import hu.bme.mit.gamma.statechart.interface_.TimeSpecification;
import hu.bme.mit.gamma.theta.verification.ThetaTraceGenerator;
import hu.bme.mit.gamma.trace.model.ExecutionTrace;
import hu.bme.mit.gamma.trace.util.TraceUtil;
import hu.bme.mit.gamma.transformation.util.GammaFileNamer;
import hu.bme.mit.gamma.transformation.util.StatechartEcoreUtil;
import hu.bme.mit.gamma.util.FileUtil;
import hu.bme.mit.gamma.verification.result.ThreeStateBoolean;

public class TraceGenerationHandler extends TaskHandler {

	protected boolean serializeTest; // Denotes whether test code is generated
	protected String testFolderUri;
	// targetFolderUri is traceFolderUri 
	protected String svgFileName; // Set in setVerification
	protected final String traceFileName = "ExecutionTrace";
	protected final String testFileName = traceFileName + "Simulation";

	protected TimeSpecification timeout = null;
	
	//
	
	protected final List<ExecutionTrace> traces = new ArrayList<ExecutionTrace>();
	
	//
	
	protected final TraceUtil traceUtil = TraceUtil.INSTANCE;
	protected final PropertyUtil propertyUtil = PropertyUtil.INSTANCE;
	protected final StatechartEcoreUtil statechartEcoreUtil = StatechartEcoreUtil.INSTANCE;
	protected final ExecutionTraceSerializer serializer = ExecutionTraceSerializer.INSTANCE;
	
	public TraceGenerationHandler(IFile file) {
		super(file);
	}
		
	public void execute(TraceGeneration traceGeneration) throws IOException {
		setTargetFolder(traceGeneration);
		
		// Setting the timeout
		this.timeout = traceGeneration.getTimeout();
		
		File targetFolder = new File(targetFolderUri + File.separator + file.getName().split("\\.")[0] + File.separator + traceGeneration.getFileName().get(0).split("\\.")[0] + File.separator);

		logger.log(Level.INFO, targetFolder.getAbsolutePath());
		if (targetFolder.exists()) {
			cleanFolder(targetFolder);			
		} else {
			targetFolder.mkdirs();
		}
		
		// Based on the method setVerification in VerificationHandler
		boolean fullTraces = traceGeneration.isFullTraces();
		Resource resource = traceGeneration.eResource();
		File file = (resource != null) ?
				ecoreUtil.getFile(resource).getParentFile() : // If Verification is contained in a resource
					fileUtil.toFile(super.file).getParentFile(); // If Verification is created in Java
		// Setting the file paths
		traceGeneration.getFileName().replaceAll(it -> fileUtil.exploreRelativeFile(file, it).toString());

		List<String> variableList = traceGeneration.getVariables();
		boolean useAbstraction = traceGeneration.getVariableLists().size() != 0;
		
		boolean noTransitionCoverage = traceGeneration.isNoTransitionCoverage();
		String filePath = traceGeneration.getFileName().get(0);
		File modelFile = new File(filePath);
		List<ExecutionTrace> retrievedTraces = new ArrayList<ExecutionTrace>();
		ThetaTraceGenerator ttg = new ThetaTraceGenerator();
		
		long timeoutInMilliseconds = (timeout == null) ? -1 : expressionEvaluator.evaluateInteger(
				StatechartModelDerivedFeatures.getTimeInMilliseconds(timeout));
		
		// Record start time
    	long startTime = System.currentTimeMillis();
		
		retrievedTraces = ttg.execute(modelFile, fullTraces, variableList, noTransitionCoverage, useAbstraction, timeoutInMilliseconds, TimeUnit.MILLISECONDS);
		
		// Record end time
    	long endTime = System.currentTimeMillis();
		// Calculate execution time
	    long timeSpentMillis = endTime - startTime;
	    logger.log(Level.INFO, "Process completed in " + timeSpentMillis + " milliseconds.");

	    long traceNum = 0;
	    if(retrievedTraces==null) {
		    logger.log(Level.INFO, "Number of received traces: 0");	    	
	    } else {
	    	traceNum = retrievedTraces.size();
		    logger.log(Level.INFO, "Number of received traces: " + traceNum);	    	
	    }
		
	    if(retrievedTraces!=null) {
	    	for (ExecutionTrace trace : retrievedTraces) {
			serializer.serialize(targetFolder.getAbsolutePath(), traceFileName, svgFileName,
					testFolderUri, testFileName, "", trace);
	    	}
		
	    	traces.addAll(retrievedTraces);

	    }
	    // Write time and trace count to a file in the target folder
	    File reportFile = new File(modelFile.getParent(), "gamma-report.txt");
	    // Ensure the file exists or is created
	    if (!reportFile.exists()) {
	        if (reportFile.createNewFile()) {
	            logger.log(Level.INFO, "Report file created: " + reportFile.getAbsolutePath());
	        } else {
	            logger.log(Level.WARNING, "Failed to create report file: " + reportFile.getAbsolutePath());
	        }
	    }
	    try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
	        writer.write("Execution Time (ms): " + timeSpentMillis);
	        writer.newLine();
	        writer.write("Number of Traces: " + traceNum);
	        writer.newLine();
	    }
	}

	public static void cleanFolder(File folder) {
		File[] files = folder.listFiles();
	    if (files != null) {
	        for (File file : files) {
	            if (file.isDirectory()) {
	                deleteFolder(file);
	            } else {
	                file.delete();
	            }
	        }
	    }
	}
	
	public static void deleteFolder(File folder) {
	    File[] files = folder.listFiles();
	    if (files!=null) {
	        for (File file : files) {
	            if (file.isDirectory()) {
	                deleteFolder(file);
	            } else {
	                file.delete();
	            }
	        }
	    }
	    folder.delete();
	}
	
	public List<ExecutionTrace> getTraces() {
		return traces;
	}
	
	public static class ExecutionTraceSerializer {
		//
		public static ExecutionTraceSerializer INSTANCE = new ExecutionTraceSerializer();
		protected ExecutionTraceSerializer() {}
		//
		protected final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		protected final FileUtil fileUtil = FileUtil.INSTANCE;
		protected final ModelSerializer serializer = ModelSerializer.INSTANCE;
		
		public void serialize(String traceFolderUri, String traceFileName, ExecutionTrace trace) throws IOException {
			this.serialize(traceFolderUri, traceFileName, null, null, null, trace);
		}
		
		public void serialize(String traceFolderUri, String traceFileName,
				String testFolderUri, String testFileName, String basePackage, ExecutionTrace trace) throws IOException {
			this.serialize(traceFolderUri, traceFileName, null, testFolderUri, testFileName, basePackage, trace);
		}
		
		public void serialize(String traceFolderUri, String traceFileName, String svgFileName,
				String testFolderUri, String testFileName, String basePackage, ExecutionTrace trace) throws IOException {
			
			// Model
			Entry<String, Integer> fileNamePair = fileUtil.getFileName(new File(traceFolderUri),
					traceFileName, GammaFileNamer.EXECUTION_XTEXT_EXTENSION);
			String fileName = fileNamePair.getKey();
			Integer id = fileNamePair.getValue();
			serializer.saveModel(trace, traceFolderUri, fileName);
		}
		
		@SuppressWarnings("unused")
		public static class VerificationResult {
			
			private String query;
			private ThreeStateBoolean result;
			private String[] parameters;
			private String executionTime;
			
			public VerificationResult(String query, ThreeStateBoolean result) {
				this(query, result, null, null);
			}
			
			public VerificationResult(String query, ThreeStateBoolean result,
					String[] parameters, String executionTime) {
				this.query = query;
				this.result = result;
				this.parameters = parameters;
				this.executionTime = executionTime;
			}
			
		}
		
	}
	
}