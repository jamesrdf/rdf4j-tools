/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.console;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.common.io.IOUtil;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.RepositoryReadOnlyException;
import org.eclipse.rdf4j.repository.config.ConfigTemplate;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create command
 *
 * @author Dale Visser
 */
public class Create implements Command {

	private static final Logger LOGGER = LoggerFactory.getLogger(Create.class);

	private static final String TEMPLATES_DIR = "templates";

	private final ConsoleIO consoleIO;
	private final ConsoleState state;
	private final LockRemover lockRemover;

	/**
	 * Constructor
	 *
	 * @param consoleIO
	 * @param state
	 * @param lockRemover
	 */
	Create(ConsoleIO consoleIO, ConsoleState state, LockRemover lockRemover) {
		this.consoleIO = consoleIO;
		this.state = state;
		this.lockRemover = lockRemover;
	}

	@Override
	public void execute(String... tokens) throws IOException {
		if (tokens.length < 2) {
			consoleIO.writeln(PrintHelp.CREATE);
		} else {
			createRepository(tokens[1]);
		}
	}

	/**
	 * Create a new repository based on a template
	 * 
	 * @param templateName name of the template
	 * @throws IOException 
	 */
	private void createRepository(final String templateName) throws IOException {
		try {
			// FIXME: remove assumption of .ttl extension
			final String templateFileName = templateName + ".ttl";
			final File templatesDir = new File(state.getDataDirectory(), TEMPLATES_DIR);
			final File templateFile = new File(templatesDir, templateFileName);
			
			InputStream templateStream = createTemplateStream(templateName, templateFileName, templatesDir,
					templateFile);
			if (templateStream != null) {
				String template;
				try {
					template = IOUtil.readString(new InputStreamReader(templateStream, "UTF-8"));
				} finally {
					templateStream.close();
				}
				final ConfigTemplate configTemplate = new ConfigTemplate(template);
				final Map<String, String> valueMap = new HashMap<>();
				final Map<String, List<String>> variableMap = configTemplate.getVariableMap();
			
				boolean eof = inputParameters(valueMap, variableMap, configTemplate.getMultilineMap());
				if (!eof) {
					final String configString = configTemplate.render(valueMap);
					final Model graph = new LinkedHashModel();
					
					final RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE,
							SimpleValueFactory.getInstance());
					rdfParser.setRDFHandler(new StatementCollector(graph));
					rdfParser.parse(new StringReader(configString), RepositoryConfigSchema.NAMESPACE);
					
					final Resource repositoryNode = Models.subject(
							graph.filter(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY)).orElseThrow(
							() -> new RepositoryConfigException("missing repository node"));
					
					final RepositoryConfig repConfig = RepositoryConfig.create(graph, repositoryNode);
					repConfig.validate();
					
					String overwrite = "WARNING: you are about to overwrite the configuration of an existing repository!";
					boolean proceedOverwrite = this.state.getManager().hasRepositoryConfig(
							repConfig.getID()) ? consoleIO.askProceed(overwrite, false) : true;
					
					String suggested = this.state.getManager().getNewRepositoryID(repConfig.getID());
					String invalid = "WARNING: There are potentially incompatible characters in the repository id.";
					boolean proceedInvalid = !suggested.startsWith(repConfig.getID())
							? consoleIO.askProceed(invalid, false) : true;
					
					if (proceedInvalid && proceedOverwrite) {
						try {
							this.state.getManager().addRepositoryConfig(repConfig);
							consoleIO.writeln("Repository created");
						} catch (RepositoryReadOnlyException e) {
							if (lockRemover.tryToRemoveLock(this.state.getManager().getSystemRepository())) {
								this.state.getManager().addRepositoryConfig(repConfig);
								consoleIO.writeln("Repository created");
							} else {
								consoleIO.writeError("Failed to create repository");
								LOGGER.error("Failed to create repository", e);
							}
						}
					} else {
						consoleIO.writeln("Create aborted");
					}
				}
			}
		} catch (EndOfFileException | UserInterruptException e) {
			LOGGER.error("Create repository aborted", e);
			throw e;
		} catch (Exception e) {
			consoleIO.writeError(e.toString());
			LOGGER.error("Failed to create repository", e);
		}
	}

	/**
	 * Ask user to specify values for the template variables
	 * 
	 * @param valueMap
	 * @param variableMap
	 * @param multilineInput
	 * @return
	 * @throws IOException 
	 */
	private boolean inputParameters(final Map<String, String> valueMap,
			final Map<String, List<String>> variableMap, Map<String, String> multilineInput)
			throws IOException {
		if (!variableMap.isEmpty()) {
			consoleIO.writeln("Please specify values for the following variables:");
		}
		boolean eof = false;
		
		for (Map.Entry<String, List<String>> entry : variableMap.entrySet()) {
			final String var = entry.getKey();
			final List<String> values = entry.getValue();
		
			StringBuilder sb = new StringBuilder();
			sb.append(var);
			
			if (values.size() > 1) {
				sb.append(" (");
				for (int i = 0; i < values.size(); i++) {
					if (i > 0) {
						sb.append("|");
					}
					sb.append(values.get(i));
				}
				sb.append(")");
			}
			if (!values.isEmpty()) {
				sb.append(" [" + values.get(0) + "]");
			}
			String prompt = sb.append(": ").toString();
			String value = multilineInput.containsKey(var) ? consoleIO.readMultiLineInput(prompt)
															: consoleIO.readln(prompt);
			eof = (value == null);
			if (eof) {
				break; // for loop
			}
			
			value = value.trim();
			if (value.length() == 0) {
				value = null; // NOPMD
			}
			valueMap.put(var, value);
		}
		return eof;
	}

	/**
	 * Create input stream from a template file in the specified file directory.
	 * If the file cannot be found, try to read it from the embedded java resources instead.
	 * 
	 * @param templateName name of the template
	 * @param templateFileName template file name
	 * @param templatesDir template directory
	 * @param templateFile template file
	 * @return input stream of the template
	 * @throws FileNotFoundException 
	 */
	private InputStream createTemplateStream(final String templateName, final String templateFileName,
			final File templatesDir, final File templateFile)
			throws FileNotFoundException {
		InputStream templateStream = null;
		if (templateFile.exists()) {
			if (templateFile.canRead()) {
				templateStream = new FileInputStream(templateFile);
			} else {
				consoleIO.writeError("Not allowed to read template file: " + templateFile);
			}
		} else {
			// Try class path for built-ins
			templateStream = RepositoryConfig.class.getResourceAsStream(templateFileName);
			if (templateStream == null) {
				consoleIO.writeError("No template called " + templateName + " found in " + templatesDir);
			}
		}
		return templateStream;
	}
}
