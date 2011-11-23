/* Copyright 2009-2011 David Hadka
 * 
 * This file is part of the MOEA Framework.
 * 
 * The MOEA Framework is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or (at your 
 * option) any later version.
 * 
 * The MOEA Framework is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public 
 * License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License 
 * along with the MOEA Framework.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.moeaframework.analysis.sensitivity;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.moeaframework.core.FrameworkException;
import org.moeaframework.core.Indicator;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PopulationIO;
import org.moeaframework.core.Problem;
import org.moeaframework.core.indicator.AdditiveEpsilonIndicator;
import org.moeaframework.core.indicator.GenerationalDistance;
import org.moeaframework.core.indicator.Hypervolume;
import org.moeaframework.core.indicator.InvertedGenerationalDistance;
import org.moeaframework.core.indicator.MaximumParetoFrontError;
import org.moeaframework.core.indicator.Spacing;
import org.moeaframework.core.spi.ProblemFactory;
import org.moeaframework.util.CommandLineUtility;
import org.moeaframework.util.OptionCompleter;

/**
 * Command line utility for extracting data from a result file.  The data that
 * can be extracted includes any properties by providing its full name, 
 * metrics using one of the following special commands:
 * <ul>
 *   <li>{@code +hypervolume} for {@link Hypervolume}
 *   <li>{@code +generational} for {@link GenerationalDistance}
 *   <li>{@code +inverted} for {@link InvertedGenerationalDistance}
 *   <li>{@code +epsilon} for {@link AdditiveEpsilonIndicator}
 *   <li>{@code +error} for {@link MaximumParetoFrontError}
 *   <li>{@code +spacing} for {@link Spacing}
 * </ul>
 */
public class ExtractData extends CommandLineUtility {
	
	/**
	 * The problem.
	 */
	private Problem problem;
	
	/**
	 * The reference set; {@code null} if the reference set has not yet been
	 * loaded.
	 */
	private NondominatedPopulation referenceSet;

	/**
	 * Private constructor to prevent instantiation.
	 */
	private ExtractData() {
		super();
	}

	@SuppressWarnings("static-access")
	@Override
	public Options getOptions() {
		Options options = super.getOptions();
		
		options.addOption(OptionBuilder
				.withLongOpt("problem")
				.hasArg()
				.withArgName("name")
				.withDescription("Problem name")
				.isRequired()
				.create('b'));
		options.addOption(OptionBuilder
				.withLongOpt("input")
				.hasArg()
				.withArgName("file")
				.withDescription("Input file")
				.isRequired()
				.create('i'));
		options.addOption(OptionBuilder
				.withLongOpt("output")
				.hasArg()
				.withArgName("file")
				.withDescription("Output file")
				.isRequired()
				.create('o'));
		options.addOption(OptionBuilder
				.withLongOpt("separator")
				.hasArg()
				.withArgName("value")
				.withDescription("Separator between entries")
				.create('s'));
		options.addOption(OptionBuilder
				.withLongOpt("noheader")
				.withDescription("Do not print header line")
				.create('h'));
		
		return options;
	}

	@Override
	public void run(CommandLine commandLine) throws Exception {
		String separator = commandLine.hasOption("separator") ? commandLine
				.getOptionValue("separator") : " ";

		String[] fields = commandLine.getArgs();

		// indicators are prepared, run the data extraction routine
		ResultFileReader input = null;
		PrintStream output = null;

		try {
			problem = ProblemFactory.getInstance().getProblem(commandLine
					.getOptionValue("problem"));
			
			try {
				input = new ResultFileReader(problem, new File(commandLine
						.getOptionValue("input")));

				try {
					output = commandLine.hasOption("output") ? new PrintStream(
							new File(commandLine.getOptionValue("output")))
							: System.out;

					// optionally print header line
					if (!commandLine.hasOption("noheader")) {
						for (int i = 0; i < fields.length; i++) {
							if (i > 0) {
								output.print(separator);
							}

							output.print(fields[i]);
						}

						output.println();
					}

					// process entries
					while (input.hasNext()) {
						ResultEntry entry = input.next();
						Properties properties = entry.getProperties();

						for (int i = 0; i < fields.length; i++) {
							if (i > 0) {
								output.print(separator);
							}

							if (properties.containsKey(fields[i])) {
								output.print(properties.getProperty(fields[i]));
							} else if (fields[i].startsWith("+")) {
								output.print(evaluate(fields[i].substring(1),
										entry, commandLine));
							} else {
								throw new FrameworkException("missing field");
							}
						}

						output.println();
					}
				} finally {
					if ((output != null) && (output != System.out)) {
						output.close();
					}
				}
			} finally {
				if (input != null) {
					input.close();
				}
			}
		} finally {
			if (problem != null) {
				problem.close();
			}
		}
	}
	
	/**
	 * Evaluates the special commands.  The {@code +} prefix should be removed
	 * prior to calling this method.  An {@link OptionCompleter} is used to
	 * auto-complete the commands, so only the unique prefix must be provided.
	 * 
	 * @param command the command identifier
	 * @param entry the entry in the result file
	 * @param commandLine the command line options
	 * @return the value of the special command
	 * @throws FrameworkException if the command is not supported
	 * @throws IOException if an I/O error occurred
	 */
	protected String evaluate(String command, ResultEntry entry, 
			CommandLine commandLine) throws IOException {
		OptionCompleter completer = new OptionCompleter("hypervolume",
				"generational", "inverted", "epsilon", "error", "spacing");
		String option = completer.lookup(command);
		
		if (option == null) {
			throw new FrameworkException("unsupported command");
		}
		
		//load the reference set
		if (referenceSet == null) {
			if (commandLine.hasOption("reference")) {
				referenceSet = new NondominatedPopulation(
						PopulationIO.readObjectives(new File(
								commandLine.getOptionValue("reference"))));
			} else {
				referenceSet = ProblemFactory.getInstance().getReferenceSet(
						commandLine.getOptionValue("problem"));
			}
			
			if (referenceSet == null) {
				throw new FrameworkException("no reference set available");
			}
		}
		
		//create the indicator, these should perhaps be cached for speed
		Indicator indicator = null;
		
		if (option.equals("hypervolume")) {
			indicator = new Hypervolume(problem, referenceSet);
		} else if (option.equals("generational")) {
			indicator = new GenerationalDistance(problem, referenceSet);
		} else if (option.equals("inverted")) {
			indicator = new InvertedGenerationalDistance(problem, referenceSet);
		} else if (option.equals("epsilon")) {
			indicator = new AdditiveEpsilonIndicator(problem, referenceSet);
		} else if (option.equals("error")) {
			indicator = new MaximumParetoFrontError(problem, referenceSet);
		} else if (option.equals("spacing")) {
			indicator = new Spacing(problem);
		} else {
			throw new IllegalStateException();
		}
		
		return Double.toString(indicator.evaluate(entry.getPopulation()));
	}
	
	/**
	 * Starts the command line utility for extracting data from a result file.
	 * 
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		new ExtractData().start(args);
	}

}