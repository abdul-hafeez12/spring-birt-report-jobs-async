package com.acme;

import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.RenderOption;

import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;

import jakarta.annotation.PreDestroy;

@Component
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	private IReportEngine engine; // stays null until the first report is requested

	private final ReportDesigns designs;

	public ReportService(ReportDesigns designs) {
		this.designs = designs;
	}

	// Starts BIRT the first time it is needed. "synchronized" means: if two jobs
	// ask at
	// the same moment, only one starts the engine and the other waits for it.
	private synchronized IReportEngine getEngine() throws Exception {
		if (engine == null) {
			log.info("Starting BIRT engine (first report request)...");
			EngineConfig config = new EngineConfig();
			Platform.startup(config);

			IReportEngineFactory factory = (IReportEngineFactory) Platform
					.createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY);
			engine = factory.createReportEngine(config);
			log.info("BIRT engine started");
		}
		return engine;
	}

	@PreDestroy
	public synchronized void stopEngine() {
		if (engine != null) {
			engine.destroy();
			Platform.shutdown();
			engine = null;
			log.info("BIRT engine stopped");
		}
	}

	public String generate(String jobId, String design, ReportFormat format, Map<String, Object> parameters)
			throws Exception {
		Path designFile = designs.resolve(design); // validates the name, finds the file
		Path outDir = Path.of("report-output");
		Files.createDirectories(outDir);
		Path outFile = outDir.resolve("report-" + jobId + "." + format.extension());

		IReportEngine birt = getEngine();
		IReportRunnable runnable = birt.openReportDesign(designFile.toString());

// Ask the report which parameters it has and what type each one is
		Map<String, Integer> types = new HashMap<>();
		IGetParameterDefinitionTask defTask = birt.createGetParameterDefinitionTask(runnable);
		try {
			for (Object defn : defTask.getParameterDefns(false)) {
				if (defn instanceof IScalarParameterDefn p) {
					types.put(p.getName(), p.getDataType());
				}
			}
		} finally {
			defTask.close();
		}

		IRunAndRenderTask task = birt.createRunAndRenderTask(runnable);
		try {
			Map<String, Object> given = (parameters != null) ? parameters : Map.of();
			for (Map.Entry<String, Object> entry : given.entrySet()) {
				Integer type = types.get(entry.getKey());
				if (type == null) {
					throw new IllegalArgumentException("Report '" + design + "' has no parameter '" + entry.getKey()
							+ "'. Available: " + types.keySet());
				}
				task.setParameterValue(entry.getKey(), convert(entry.getKey(), entry.getValue(), type));
			}

			RenderOption options = new RenderOption();
			options.setOutputFormat(format.extension());
			options.setOutputFileName(outFile.toAbsolutePath().toString());
			task.setRenderOption(options);

			task.run();

			List<?> errors = task.getErrors();
			if (!errors.isEmpty()) {
				throw new IllegalStateException("Report had errors: " + errors.get(0));
			}
		} finally {
			task.close();
		}
		return outFile.toAbsolutePath().toString();
	}

    //Turns the client's value (from JSON) into the type the report parameter expects
	private static Object convert(String name, Object value, int type) {
		String text = String.valueOf(value).trim();
		try {
			switch (type) {
			case IScalarParameterDefn.TYPE_INTEGER:
				return Integer.valueOf(text);
			case IScalarParameterDefn.TYPE_FLOAT:
				return Double.valueOf(text);
			case IScalarParameterDefn.TYPE_DECIMAL:
				return new BigDecimal(text);
			case IScalarParameterDefn.TYPE_BOOLEAN:
				return Boolean.valueOf(text);
			case IScalarParameterDefn.TYPE_DATE:
				return Date.valueOf(text); // 2026-10-16
			case IScalarParameterDefn.TYPE_TIME:
				return Time.valueOf(text); // 14:30:00
			case IScalarParameterDefn.TYPE_DATE_TIME:
				return Timestamp.valueOf(text); // 2026-10-16 14:30:00
			default:
				return text; // strings and the rest
			}
		} catch (IllegalArgumentException e) { // NumberFormatException is a kind of this
			throw new IllegalArgumentException("Parameter '" + name + "' has a wrong value: '" + text + "'");
		}
	}

	
}