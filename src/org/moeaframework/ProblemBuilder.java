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
package org.moeaframework;

import java.io.File;
import java.io.IOException;
import org.moeaframework.core.EpsilonBoxDominanceArchive;
import org.moeaframework.core.FrameworkException;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PopulationIO;
import org.moeaframework.core.Problem;
import org.moeaframework.core.comparator.AggregateConstraintComparator;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.EpsilonBoxConstraintComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.spi.ProblemFactory;

/**
 * Provides builder methods for instantiating problems and their reference sets.
 * This is an internal class with all package-private methods, allowing 
 * subclasses to selectively expose the methods.
 */
class ProblemBuilder {

	/**
	 * The problem name.  If {@code null}, the problem is specified by
	 * {@code problemClass}.
	 */
	String problemName;
	
	/**
	 * The problem class.  If {@code null}, the problem is specified by
	 * {@code problemName}.
	 */
	Class<?> problemClass;
	
	/**
	 * The problem provider for creating problem instances; or {@code null}
	 * if the default problem factory should be used.
	 */
	ProblemFactory problemFactory;
	
	/**
	 * The &epsilon; values used by this builder.
	 */
	double[] epsilon;
	
	/**
	 * The file containing the reference set to be used by this builder; or
	 * {@code null} if the reference set should be aggregated from all
	 * individual approximation sets.
	 */
	File referenceSetFile;
	
	/**
	 * Constructs a new problem builder.
	 */
	ProblemBuilder() {
		super();
	}
	
	/**
	 * Sets the problem factory used by this builder.
	 * 
	 * @param problemFactory the problem factory
	 * @return a reference to this builder
	 */
	ProblemBuilder usingProblemFactory(ProblemFactory problemFactory) {
		this.problemFactory = problemFactory;
		
		return this;
	}
	
	/**
	 * Sets the problem used by this builder.
	 * 
	 * @param problemName the problem name
	 * @return a reference to this builder
	 */
	ProblemBuilder withProblem(String problemName) {
		this.problemName = problemName;
		this.problemClass = null;
		
		return this;
	}
	
	/**
	 * Sets the problem used by this builder.  
	 * 
	 * @param problemClass the problem class
	 * @return a reference to this builder
	 */
	ProblemBuilder withProblemClass(Class<?> problemClass) {
		this.problemClass = problemClass;
		this.problemName = null;
		
		return this;
	}
	
	/**
	 * Sets the problem used by this builder.  
	 * 
	 * @param problemClassName the problem class name
	 * @return a reference to this builder
	 * @throws ClassNotFoundException if the specified problem class name could
	 *         not be found
	 */
	ProblemBuilder withProblemClass(String problemClassName) 
	throws ClassNotFoundException {
		withProblemClass(Class.forName(problemClassName));
		
		return this;
	}
	
	/**
	 * Sets the &epsilon; values used by this builder, specifying the archive
	 * returned by {@link #newArchive()}.
	 * 
	 * @param epsilon the &epsilon; values
	 * @return a reference to this builder
	 */
	ProblemBuilder withEpsilon(double... epsilon) {
		if ((epsilon == null) || (epsilon.length == 0)) {
			this.epsilon = null;
		} else {
			this.epsilon = epsilon;
		}
		
		return this;
	}
	
	/**
	 * Sets the file containing the reference set to be used by this builder.
	 * If not specified, the reference set should be aggregated from all
	 * individual approximation sets.
	 * 
	 * @param referenceSetFile the reference set file
	 * @return a reference to this builder
	 */
	ProblemBuilder withReferenceSet(File referenceSetFile) {
		this.referenceSetFile = referenceSetFile;
		
		return this;
	}
	
	/**
	 * Returns an empty non-dominated population or &epsilon;-box dominance
	 * archive, depending on whether the {@code epsilon} field is set.  This is
	 * the archive used to store the reference set.
	 * 
	 * @return an empty non-dominated population or &epsilon;-box dominance
	 *         archive, depending on whether the {@code epsilon} field is set.
	 */
	NondominatedPopulation newArchive() {
		if (epsilon == null) {
			return new NondominatedPopulation(new ChainedComparator(
					new AggregateConstraintComparator(),
					new ParetoDominanceComparator()));
		} else {
			return new EpsilonBoxDominanceArchive(
					new EpsilonBoxConstraintComparator(epsilon));
		}
	}
	
	/**
	 * Returns the reference set used by this builder.  The reference set is
	 * generated as follows:
	 * <ol>
	 *   <li>If {@link #withReferenceSet(File)} has been set, the contents of 
	 *       the reference set file are returned;
	 *   <li>If the problem factory provides a reference set via the
	 *       {@link ProblemFactory#getReferenceSet(String)} method, this
	 *       reference set is returned;
	 *   <li>Otherwise, an exception is thrown.
	 * </ol>
	 * 
	 * @return the reference set used by this builder
	 * @throws IllegalArgumentException if no reference set is available or
	 *         could not be loaded
	 */
	NondominatedPopulation getReferenceSet() {
		NondominatedPopulation referenceSet = newArchive();
		
		if (referenceSetFile == null) {
			//determine if the problem factory provides a reference set
			NondominatedPopulation factorySet = null;
			
			if (problemName != null) {
				if (problemFactory == null) {
					factorySet = ProblemFactory.getInstance()
							.getReferenceSet(problemName);
				} else {
					factorySet = problemFactory.getReferenceSet(problemName);
				}
			}
			
			if (factorySet == null) {
				throw new IllegalArgumentException(
						"no reference set available");
			} else {
				referenceSet.addAll(factorySet);
			}
		} else {
			try {
				referenceSet.addAll(PopulationIO.readObjectives(
						referenceSetFile));
			} catch (IOException e) {
				throw new IllegalArgumentException(
						"unable to load reference set", e);
			}
		}
		
		return referenceSet;
	}
	
	/**
	 * Returns a new instance of the problem used by this builder, or throws
	 * an exception if no problem has been defined.  The code requesting the
	 * problem instance is expected to close the problem when finished.
	 * 
	 * @return a new instance of the problem used by this builder, or throws
	 *         an exception if no problem has been defined
	 * @throws IllegalArgumentException if no problem has been defined
	 */
	Problem getProblemInstance() {
		if ((problemName == null) && (problemClass == null)) {
			throw new IllegalArgumentException("no problem specified");
		}
		
		if (problemClass != null) {
			try {
				return (Problem)problemClass.newInstance();
			} catch (InstantiationException e) {
				throw new FrameworkException(e);
			} catch (IllegalAccessException e) {
				throw new FrameworkException(e);
			}
		} else if (problemFactory == null) {
			return ProblemFactory.getInstance().getProblem(problemName);
		} else {
			return problemFactory.getProblem(problemName);
		}
	}
	
}