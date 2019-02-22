package gdbuildmaker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class BuildWalker {
	private final class BuildBytes implements Comparable<BuildBytes> {
		private final byte[] bytes;
		
		private BuildBytes(Collection<Constellation> build) {
			bytes = new byte[build.size()];
			int i = 0;
			for (Constellation c : build) {
				bytes[i++] = (byte)(c.getOrdinal() - Byte.MIN_VALUE);
			}
		}
		
		public int hashCode() {
			return Arrays.hashCode(bytes);
		}
		
		public boolean equals(Object other) {
			if (other == null) {return false; }
			if (!(other instanceof BuildBytes)) {return false; }
			return Arrays.equals(bytes, ((BuildBytes)other).bytes);
		}
		
		public int compareTo(BuildWalker.BuildBytes other) {
			int i = 0;
			int diff;
			while (i < bytes.length && i < other.bytes.length) {
				diff = bytes[i] - other.bytes[i];
				if (diff != 0) {
					return diff;
				}
				i++;
			}
			return bytes.length - other.bytes.length;
		}
		
		public String toString() {
			StringBuilder builder = new StringBuilder("[");
			if (bytes.length > 0) {
				builder.append(bytes[0]);
				for (int i = 1; i < bytes.length; i++) {
					builder.append(", " + bytes[i]);
				}
			}
			builder.append("]");
			return builder.toString();
		}
	}
	
	private class Walker implements Runnable {
		BuildFinisher buildFinisher;
		
		// Build variables represent the state of the build at each step in the walk
		SortedSet<Constellation> build;
		int buildStars;
		double buildValue;
		AffinityValues buildAffinities;
		
		// Options for the next step in the walk
		Stack<Constellation> options;
		
		// The history of choices the walk has taken to get to the current step
		Stack<Constellation> path;
		
		public Walker() {
			buildFinisher = new BuildFinisher();
			build = new TreeSet<Constellation>(new Comparator<Constellation>() {
					public int compare(Constellation c1, Constellation c2) {
						return c1.getOrdinal() - c2.getOrdinal();
				}
			});
			buildStars = 0;
			buildValue = 0.0;
			buildAffinities = new AffinityValues();
			
			options = new Stack<Constellation>();
			path = new Stack<Constellation>();
		}
		
		public void run() {
			pickOptions();
			while ((options.size() > 0 || path.size() > 0) && continueWalking) {
				
				// If the options for this step are empty, return to the previous step
				if (options.size() == 0) {
					Constellation popStella = path.pop();
					
					// If the popped constellation must be removed
					if (build.remove(popStella)) {
						buildStars -= popStella.numStars();
						buildValue -= constellationValues.get(popStella.getOrdinal());
						buildAffinities.subtract(popStella.getReward());
					}
					// If the popped constellation must be added back on
					else {
						build.add(popStella);
						buildStars += popStella.numStars();
						buildValue += constellationValues.get(popStella.getOrdinal());
						buildAffinities.add(popStella.getReward());
					}
					
					// Re-create the options stack for the previous step
					pickOptions();
					while (options.peek() != popStella) {options.pop(); }
					options.pop();
				}
				// Else, try proceeding to the next step by selecting the next option
				else {
					Constellation stepStella = options.pop();
					
					// If the selection is a removal
					if (build.remove(stepStella)) {
						BuildBytes buildBytes = new BuildBytes(build);
						buildAffinities.subtract(stepStella.getReward());
						// If all constellation requirements are still met
						//  and the new build can be added to the visited builds set
						if (allAvailable(build, buildAffinities) && visitedBuilds.add(buildBytes)) {
							buildStars -= stepStella.numStars();
							buildValue -= constellationValues.get(stepStella.getOrdinal());
							path.push(stepStella);
							pickOptions();
							
							// Find the best use for unspent stars
							BuildFinisher.PartialBuild partial =
									buildFinisher.bestPartialBuild(build, buildStars, buildAffinities);
							
							// Give the finished build to TopBuilds
							TopBuilds.getInstance().submit(
									build, partial.getPartials(), buildValue + partial.getValue());
						}
						// If the build is not valid or has been visited, roll back changes
						else {
							build.add(stepStella);
							buildAffinities.add(stepStella.getReward());
						}
					}
					// If the selection is an addition
					else {
						build.add(stepStella);
						BuildBytes buildBytes = new BuildBytes(build);
						
						// If the new build can be added to the visited builds set
						if (visitedBuilds.add(buildBytes)) {
							buildStars += stepStella.numStars();
							buildValue += constellationValues.get(stepStella.getOrdinal());
							buildAffinities.add(stepStella.getReward());
							path.push(stepStella);
							pickOptions();
							
							// Find the best use for unspent stars
							BuildFinisher.PartialBuild partial =
									buildFinisher.bestPartialBuild(build, buildStars, buildAffinities);
							
							// Give the finished build to TopBuilds
							TopBuilds.getInstance().submit(
									build, partial.getPartials(), buildValue + partial.getValue());
						}
						// If the build has been visited, roll back changes
						else {
							build.remove(stepStella);
						}
					}
				}
			}
		}
		
		/**
		 * Populates an options stack with an ordered list of valid constellation
		 *  selections for the next step in the walk. Removals are on the bottom
		 *  of the options stack, with lowest value removals on top. Additions are
		 *  on top of the options stack, with highest value additions on top.
		 * WARNING: For removals, pickOptions() does NOT check if the resulting
		 *  build will be valid.
		 * Uses: sortedConstellations
		 */
		private void pickOptions() {
			options.clear();

			/*
			 * Constellations are sorted from low to high value. To get the desired
			 * order, add removals to the bottom, and add additions to the top.
			 */
			for (Constellation constellation : sortedConstellations) {
				if (constellation.isAvailableWith(buildAffinities)) {
					// If this selection would be a removal
					if (build.contains(constellation)) {
						options.add(0, constellation);
					// If this addition would not put the build over the star limit
					} else if (buildStars + constellation.numStars() <= Controller.MAX_STARS) {
						options.add(constellation);
					}
				}
			}
		}
	}
	
	// Shared resources
	private List<Constellation> sortedConstellations;
	private List<Double> constellationValues;
	private Set<BuildBytes> visitedBuilds;
	private boolean continueWalking = false;
	
	private List<Thread> activeThreads;
	
	public BuildWalker(List<Constellation> sortedConstellations,
			List<Double> constellationValues, Map<Star, Double> starValues) {
		this.sortedConstellations = sortedConstellations;
		this.constellationValues = constellationValues;
		
		BuildFinisher.setup(sortedConstellations, starValues);
		
		visitedBuilds = ConcurrentHashMap.newKeySet();
		
		continueWalking = true;
		
		activeThreads = new ArrayList<Thread>();
	}
	
	public void start() {
		start(1);
	}
	
	public void start(int numThreads) {
		for (int i = 0; i < numThreads; i++) {
			Thread t = new Thread(new Walker());
			activeThreads.add(t);
			t.start();
		}
	}
	
	public void stop() {
		continueWalking = false; // signal active threads to stop
		for (Thread t : activeThreads) {
			try {
				t.join();
			} catch (InterruptedException e) {}
		}
		activeThreads.clear();
	}
	
	public int getBuildsVisited() {
		return visitedBuilds.size();
	}
	
	private boolean allAvailable(Iterable<Constellation> constellations, AffinityValues av) {
		for (Constellation c : constellations) {
			if (!(c.isAvailableWith(av))) return false;
		}
		return true;
	}
}
