package com.barchart.maven.plugin.sbe;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import uk.co.real_logic.sbe.SbeTool;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.IrDecoder;
import uk.co.real_logic.sbe.ir.IrEncoder;
import uk.co.real_logic.sbe.xml.IrGenerator;

@Mojo(name = "run", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SbeMavenPluginMojo extends AbstractMojo {

	@Component
	private MavenProject project;

	@Parameter(alias = SbeTool.TARGET_NAMESPACE, required = false)
	private String targetNamespace;

	@Parameter(alias = SbeTool.GENERATE_STUBS, required = false)
	private boolean generateStubs = true;

	@Parameter(alias = SbeTool.GENERATE_IR, required = false)
	private boolean generateIr = false;

	@Parameter(alias = SbeTool.OUTPUT_DIR, required = false)
	private String outputDir = "target/generated-sources/sbe";

	@Parameter(alias = SbeTool.TARGET_LANGUAGE, required = false)
	private String targetLanguage = "Java";

	@Parameter(alias = SbeTool.KEYWORD_APPEND_TOKEN, required = false)
	private String keywordAppendToken = "";
	
	@Parameter(alias = "resources", required = true)
	private List<String> resources;
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (!keywordAppendToken.isEmpty()) {
			getLog().info("Setting keywordAppendToken to " + keywordAppendToken);
			System.setProperty(SbeTool.KEYWORD_APPEND_TOKEN, keywordAppendToken);
		}
		getLog().info("Simple Binary Encoding Maven Plugin.");
		getLog().info("resources: " + resources);
		try {
			executeCore();
		} catch (Exception e) {
			throw new MojoExecutionException("", e);
		}
	}

	private void executeCore() throws Exception {
		getLog().info("SBE Plugin base directory: " + project.getBasedir());
		final File absoluteOutput = new File(project.getBasedir(), outputDir);
		getLog().info("SBE Plugin output directory: " + absoluteOutput);

		for (String resourceFilename : resources) {
			File resourceFile = new File(project.getBasedir(), resourceFilename);
			Ir ir = getIr(resourceFile);
			if (ir != null) {
				if (generateStubs) {
					SbeTool.generate(ir, absoluteOutput.getAbsolutePath(), targetLanguage);
				}
				if (generateIr) {
					final String inputFilename = resourceFile.getName();
					final int nameEnd = inputFilename.lastIndexOf('.');
					final String namePart = inputFilename.substring(0, nameEnd);
					final File fullPath = new File(absoluteOutput, namePart + ".sbeir");
					final IrEncoder irEncoder = new IrEncoder(fullPath.getAbsolutePath(), ir);
					try {
						irEncoder.encode();
					} finally {
						irEncoder.close();
					}
				}
			}
		}
		logGeneratedStubs(absoluteOutput);
		project.addCompileSourceRoot(absoluteOutput.getAbsolutePath());
	}

	private void logGeneratedStubs(File absoluteOutput) throws IOException {
		getLog().info("Adding generated source directory to build: " + absoluteOutput);
		@SuppressWarnings({ "unchecked" })
		List<String> fileNames = (List<String>) FileUtils.getFileNames(absoluteOutput, null, null, true);
		for (String filename : fileNames) {
			getLog().info("Generated file " + filename);
		}
	}

	private Ir getIr(File resourceFile) throws Exception {
		if (resourceFile.getName().endsWith(".xml")) {
			return new IrGenerator().generate(SbeTool.parseSchema(resourceFile.getAbsolutePath()), targetNamespace);
		} else if (resourceFile.getName().endsWith(".sbeir")) {
			IrDecoder irDecoder = new IrDecoder(resourceFile.getAbsolutePath());
			try {
				return irDecoder.decode();
			} finally {
				irDecoder.close();
			}
		} else {
			getLog().info("File format not supported.");
			return null;
		}
	}

}
