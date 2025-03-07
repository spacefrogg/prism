//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package explicit;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

import common.IteratorTools;
import explicit.graphviz.Decorator;
import parser.State;
import parser.Values;
import parser.VarList;
import prism.Evaluator;
import prism.ModelType;
import prism.PrismException;
import prism.PrismFileLog;
import prism.PrismLog;

import static prism.PrismSettings.DEFAULT_EXPORT_MODEL_PRECISION;

/**
 * Interface for (abstract) classes that provide (read-only) access to an explicit-state model.
 * This is a generic class where probabilities/rates/etc. are of type {@code Value}.
 */
public interface Model<Value>
{
	// Accessors

	/**
	 * Get the type of this model.
	 */
	public ModelType getModelType();

	/**
	 * Get the number of states.
	 */
	public int getNumStates();

	/**
	 * Get the number of initial states.
	 */
	public int getNumInitialStates();

	/**
	 * Get iterator over initial state list.
	 */
	public Iterable<Integer> getInitialStates();

	/**
	 * Get the index of the first initial state
	 * (i.e. the one with the lowest index).
	 * Returns -1 if there are no initial states.
	 */
	public int getFirstInitialState();

	/**
	 * Check whether a state is an initial state.
	 */
	public boolean isInitialState(int i);

	/**
	 * Get the number of states that are/were deadlocks.
	 * (Such states may have been fixed at build-time by adding self-loops)
	 */
	public int getNumDeadlockStates();

	/**
	 * Get iterator over states that are/were deadlocks.
	 * (Such states may have been fixed at build-time by adding self-loops)
	 */
	public Iterable<Integer> getDeadlockStates();
	
	/**
	 * Get list of states that are/were deadlocks.
	 * (Such states may have been fixed at build-time by adding self-loops)
	 */
	public StateValues getDeadlockStatesList();
	
	/**
	 * Get the index of the first state that is/was a deadlock.
	 * (i.e. the one with the lowest index).
	 * Returns -1 if there are no initial states.
	 */
	public int getFirstDeadlockState();

	/**
	 * Check whether a state is/was deadlock.
	 * (Such states may have been fixed at build-time by adding self-loops)
	 */
	public boolean isDeadlockState(int i);
	
	/**
	 * Get access to a list of states (optionally stored).
	 */
	public List<State> getStatesList();

	/** Get access to the VarList (optionally stored) */
	public VarList getVarList();

	/**
	 * Get access to a list of constant values (optionally stored).
	 */
	public Values getConstantValues();
	
	/**
	 * Get the states that satisfy a label in this model (optionally stored).
	 * Returns null if there is no label of this name.
	 */
	public BitSet getLabelStates(String name);
	
	/**
	 * Get the labels that are (optionally) stored.
	 * Returns an empty set if there are no labels.
	 */
	public Set<String> getLabels();

	/**
	 * Returns true if a label with the given name is attached to this model
	 */
	public boolean hasLabel(String name);

	/**
	 * Get the mapping from labels that are (optionally) stored
	 * to the sets of states that satisfy them.
	 */
	default Map<String, BitSet> getLabelToStatesMap()
	{
		// Default implementation creates a new map on demand
		Map<String, BitSet> labels = new TreeMap<String, BitSet>();
		for (String name : getLabels()) {
			labels.put(name, getLabelStates(name));
		}
		return labels;
	}
	
	/**
	 * Get the total number of transitions in the model.
	 */
	public default int getNumTransitions()
	{
		int numStates = getNumStates();
		int numTransitions = 0;
		for (int s = 0; s < numStates; s++) {
			numTransitions += getNumTransitions(s);
		}
		return numTransitions;
	}

	/**
	 * Get the number of transitions from state s.
	 */
	public default int getNumTransitions(int s)
	{
		return Math.toIntExact(IteratorTools.count(getSuccessorsIterator(s)));
	}

	/**
	 * Get the number of transitions leaving a set of states.
	 * <br>
	 * Default implementation: Iterator over the states and sum the result of getNumTransitions(s).
	 * @param states The set of states, specified by an OfInt iterator
	 * @return the number of transitions
	 */
	public default long getNumTransitions(PrimitiveIterator.OfInt states)
	{
		long count = 0;
		while (states.hasNext()) {
			int s = states.nextInt();
			count += getNumTransitions(s);
		}
		return count;
	}

	/**
	 * Get an iterator over the successors of state s.
	 * Default implementation via the SuccessorsIterator returned
	 * from {@code getSuccessors}, ensuring that there are no
	 * duplicates.
	 */
	public default Iterator<Integer> getSuccessorsIterator(int s)
	{
		SuccessorsIterator successors = getSuccessors(s);
		return successors.distinct();
	}

	/**
	 * Get a SuccessorsIterator for state s.
	 */
	public SuccessorsIterator getSuccessors(int s);

	/**
	 * Returns true if state s2 is a successor of state s1.
	 */
	public default boolean isSuccessor(int s1, int s2)
	{
		// the code for this method is equivalent to the following stream expression,
		// but kept explicit for performance
		//
		// return getSuccessors(s1).stream().anyMatch(
		//           (t) -> {return t == s2;}
		// );

		SuccessorsIterator it = getSuccessors(s1);
		while (it.hasNext()) {
			int t = it.nextInt();
			if (t == s2)
				return true;
		}
		return false;
	}

	/**
	 * Check if all the successor states of a state are in a set.
	 * @param s The state to check
	 * @param set The set to test for inclusion
	 */
	public default boolean allSuccessorsInSet(int s, BitSet set)
	{
		return allSuccessorsMatch(s, set::get);
	}

	/**
	 * Check if any successor states of a state are in a set.
	 * @param s The state to check
	 * @param set The set to test for inclusion
	 */
	public default boolean someSuccessorsInSet(int s, BitSet set)
	{
		return someSuccessorsMatch(s, set::get);
	}

	/**
	 * Check if all the successor states of a state match the predicate.
	 * @param s The state to check
	 * @param p the predicate
	 */
	public default boolean allSuccessorsMatch(int s, IntPredicate p)
	{
		// the code for this method is equivalent to the following stream expression,
		// but kept explicit for performance
		//
		// return getSuccessors(s).stream().allMatch(p);

		SuccessorsIterator it = getSuccessors(s);
		while (it.hasNext()) {
			int t = it.nextInt();
			if (!p.test(t))
				return false;
		}
		return true;
	}

	/**
	 * Check if any successor states of a state match the predicate.
	 * @param s The state to check
	 * @param p the predicate
	 */
	public default boolean someSuccessorsMatch(int s, IntPredicate p)
	{
		// the code for this method is equivalent to the following stream expression,
		// but kept explicit for performance
		//
		// return getSuccessors(s).stream().anyMatch(p);

		SuccessorsIterator it = getSuccessors(s);
		while (it.hasNext()) {
			int t = it.nextInt();
			if (p.test(t))
				return true;
		}
		return false;
	}

	/**
	 * Find all deadlock states and store this information in the model.
	 * If requested (if fix=true) and if needed (i.e. for DTMCs/CTMCs),
	 * fix deadlocks by adding self-loops in these states.
	 * The set of deadlocks (before any possible fixing) can be obtained from {@link #getDeadlockStates()}.
	 * @throws PrismException if the model is unable to fix deadlocks because it is non-mutable.
	 */
	public void findDeadlocks(boolean fix) throws PrismException;

	/**
	 * Checks for deadlocks and throws an exception if any exist.
	 */
	public void checkForDeadlocks() throws PrismException;

	/**
	 * Checks for deadlocks and throws an exception if any exist.
	 * States in 'except' (If non-null) are excluded from the check.
	 */
	public void checkForDeadlocks(BitSet except) throws PrismException;

	// Export methods (explicit files)

	/**
	 * Export to explicit format readable by PRISM (i.e. a .tra file, etc.).
	 */
	default void exportToPrismExplicit(String baseFilename) throws PrismException
	{
		exportToPrismExplicit(baseFilename, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to explicit format readable by PRISM (i.e. a .tra file, etc.).
	 * @param precision number of significant digits >= 1
	 */
	default void exportToPrismExplicit(String baseFilename, int precision) throws PrismException
	{
		// Default implementation - just output .tra file
		// (some models might override this)
		exportToPrismExplicitTra(baseFilename + ".tra", precision);
	}

	/**
	 * Export transition matrix to explicit format readable by PRISM (i.e. a .tra file).
	 */
	default void exportToPrismExplicitTra(String filename) throws PrismException
	{
		exportToPrismExplicitTra(filename, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export transition matrix to explicit format readable by PRISM (i.e. a .tra file).
	 * @param precision number of significant digits >= 1
	 */
	default void exportToPrismExplicitTra(String filename, int precision) throws PrismException
	{
		try (PrismFileLog log = PrismFileLog.create(filename)) {
			exportToPrismExplicitTra(log, precision);
		}
	}

	/**
	 * Export transition matrix to explicit format readable by PRISM (i.e. a .tra file).
	 */
	default void exportToPrismExplicitTra(File file) throws PrismException
	{
		exportToPrismExplicitTra(file, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export transition matrix to explicit format readable by PRISM (i.e. a .tra file).
	 * @param precision number of significant digits >= 1
	 */
	default void exportToPrismExplicitTra(File file, int precision) throws PrismException
	{
		exportToPrismExplicitTra(file.getPath(), precision);
	}


	/**
	 * Export transition matrix to explicit format readable by PRISM (i.e. a .tra file).
	 */
	default void exportToPrismExplicitTra(PrismLog log)
	{
		exportToPrismExplicitTra(log, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export transition matrix to explicit format readable by PRISM (i.e. a .tra file).
	 * @param precision number of significant digits >= 1
	 */
	public void exportToPrismExplicitTra(PrismLog log, int precision);
	
	// Export methods (dot files)

	/**
	 * Export to a dot file.
	 * @param filename Name of file to export to
	 */
	default void exportToDotFile(String filename) throws PrismException
	{
		exportToDotFile(filename, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file.
	 * @param filename Name of file to export to
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(String filename, int precision) throws PrismException
	{
		try (PrismFileLog log = PrismFileLog.create(filename)) {
			exportToDotFile(log, precision);
		}
	}

	/**
	 * Export to a dot file, highlighting states in 'mark'.
	 * @param filename Name of file to export to
	 * @param mark States to highlight (ignored if null)
	 */
	default void exportToDotFile(String filename, BitSet mark) throws PrismException
	{
		exportToDotFile(filename, mark, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file, highlighting states in 'mark'.
	 * @param filename Name of file to export to
	 * @param mark States to highlight (ignored if null)
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(String filename, BitSet mark, int precision) throws PrismException
	{
		try (PrismFileLog log = PrismFileLog.create(filename)) {
			exportToDotFile(log, mark, precision);
		}
	}

	/**
	 * Export to a dot file, decorating states and transitions with the provided decorators
	 * @param filename Name of the file to export to
	 */
	default void exportToDotFile(String filename, Iterable<explicit.graphviz.Decorator> decorators) throws PrismException
	{
		exportToDotFile(filename, decorators, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file, decorating states and transitions with the provided decorators
	 * @param filename Name of the file to export to
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(String filename, Iterable<explicit.graphviz.Decorator> decorators, int precision) throws PrismException
	{
		try (PrismFileLog log = PrismFileLog.create(filename)) {
			exportToDotFile(log, decorators, precision);
		}
	}

	/**
	 * Export to a dot file.
	 * @param out PrismLog to export to
	 */
	default void exportToDotFile(PrismLog out)
	{
		exportToDotFile(out, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file.
	 * @param out PrismLog to export to
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(PrismLog out, int precision)
	{
		exportToDotFile(out, (Iterable<explicit.graphviz.Decorator>)null, precision);
	}

	/**
	 * Export to a dot file, highlighting states in 'mark'.
	 * @param out PrismLog to export to
	 * @param mark States to highlight (ignored if null)
	 */
	default void exportToDotFile(PrismLog out, BitSet mark)
	{
		exportToDotFile(out, mark, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file, highlighting states in 'mark'.
	 * @param out PrismLog to export to
	 * @param mark States to highlight (ignored if null)
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(PrismLog out, BitSet mark, int precision)
	{
		if (mark == null) {
			exportToDotFile(out, precision);
		}
		exportToDotFile(out, Collections.singleton(new explicit.graphviz.MarkStateSetDecorator(mark)), precision);
	}

	/**
	 * Export to a dot file, highlighting states in 'mark'.
	 * @param out PrismLog to export to
	 * @param mark States to highlight (ignored if null)
	 * @param showStates Show state info on nodes?
	 */
	default void exportToDotFile(PrismLog out, BitSet mark, boolean showStates)
	{
		exportToDotFile(out, mark, showStates, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file, highlighting states in 'mark'.
	 * @param out PrismLog to export to
	 * @param mark States to highlight (ignored if null)
	 * @param showStates Show state info on nodes?
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(PrismLog out, BitSet mark, boolean showStates, int precision)
	{
		ArrayList<explicit.graphviz.Decorator> decorators = new ArrayList<explicit.graphviz.Decorator>();
		if (showStates) {
			if (getModelType().partiallyObservable()) {
				decorators.add(new explicit.graphviz.ShowStatesDecorator(getStatesList(), ((PartiallyObservableModel) this)::getObservationAsState));
			} else {
				decorators.add(new explicit.graphviz.ShowStatesDecorator(getStatesList()));
			}
		}
		if (mark != null) {
			decorators.add(new explicit.graphviz.MarkStateSetDecorator(mark));
		}
		exportToDotFile(out, decorators, precision);
	}

	/**
	 * Export to a dot file, decorating states and transitions with the provided decorators
	 * @param out PrismLog to export to
	 */
	default void exportToDotFile(PrismLog out, Iterable<explicit.graphviz.Decorator> decorators)
	{
		exportToDotFile(out, decorators, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a dot file, decorating states and transitions with the provided decorators
	 * @param out PrismLog to export to
	 * @param precision number of significant digits >= 1
	 */
	default void exportToDotFile(PrismLog out, Iterable<explicit.graphviz.Decorator> decorators, int precision)
	{
		explicit.graphviz.Decoration defaults = new explicit.graphviz.Decoration();
		defaults.attributes().put("shape", "box");

		// Header
		out.print("digraph " + getModelType() + " {\nnode " + defaults.toString() + ";\n");
		int i, numStates;
		for (i = 0, numStates = getNumStates(); i < numStates; i++) {
			// initialize
			explicit.graphviz.Decoration d = new explicit.graphviz.Decoration(defaults);
			d.setLabel(Integer.toString(i));

			// run any decorators
			if (decorators != null) {
				for (Decorator decorator : decorators) {
					d = decorator.decorateState(i, d);
				}
			}

			String decoration = d.toString();
			out.println(i + " " + decoration + ";");

			// Transitions for state i
			exportTransitionsToDotFile(i, out, decorators, precision);
		}

		// Footer
		out.print("}\n");
	}

	/**
	 * Export the transitions from state {@code i} in Dot format to {@code out},
	 * decorating using the given decorators.
	 * <br>
	 * The default implementation throws an UnsupportedOperationException,
	 * so this method should be overloaded.
	 *
	 * @param i State index
	 * @param out PrismLog for output
	 * @param decorators the decorators (may be {@code null})
	 */
	default void exportTransitionsToDotFile(int i, PrismLog out, Iterable<explicit.graphviz.Decorator> decorators)
	{
		exportTransitionsToDotFile(i, out, decorators, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export the transitions from state {@code i} in Dot format to {@code out},
	 * decorating using the given decorators.
	 * <br>
	 * The default implementation throws an UnsupportedOperationException,
	 * so this method should be overloaded.
	 *
	 * @param i State index
	 * @param out PrismLog for output
	 * @param decorators the decorators (may be {@code null})
	 * @param precision number of significant digits >= 1
	 */
	default void exportTransitionsToDotFile(int i, PrismLog out, Iterable<explicit.graphviz.Decorator> decorators, int precision)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Export to a equivalent PRISM language model description.
	 */
	default void exportToPrismLanguage(String filename) throws PrismException
	{
		exportToPrismLanguage(filename, DEFAULT_EXPORT_MODEL_PRECISION);
	}

	/**
	 * Export to a equivalent PRISM language model description.
	 * @param precision number of significant digits >= 1
	 */
	public void exportToPrismLanguage(String filename, int precision) throws PrismException;
	
	/**
	 * Export states list.
	 */
	public void exportStates(int exportType, VarList varList, PrismLog log) throws PrismException;
	
	/**
	 * Report info/stats about the model as a string.
	 */
	public String infoString();

	/**
	 * Report info/stats about the model, tabulated, as a string.
	 */
	public String infoStringTable();

	/** Has this model a stored PredecessorRelation? */
	public boolean hasStoredPredecessorRelation();

	/**
	 * If there is a PredecessorRelation stored for this model, return that.
	 * Otherwise, create one and return that. If {@code storeIfNew},
	 * store it for later use.
	 *
	 * @param parent a PrismComponent (for obtaining the log)
	 * @param storeIfNew if the predecessor relation is newly created, store it
	 */
	public PredecessorRelation getPredecessorRelation(prism.PrismComponent parent, boolean storeIfNew);

	/** Clear any stored predecessor relation, e.g., because the model was modified */
	public void clearPredecessorRelation();

	/**
	 * Get an Evaluator for the values stored in this Model for probabilities etc.
	 * This is needed, for example, to compute probability sums, check for equality to 0/1, etc.
	 * A default implementation provides an evaluator for the (usual) case when Value is Double.
	 */
	@SuppressWarnings("unchecked")
	public default Evaluator<Value> getEvaluator()
	{
		return (Evaluator<Value>) Evaluator.createForDoubles();
	}
}
