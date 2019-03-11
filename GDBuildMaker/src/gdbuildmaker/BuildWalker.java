package gdbuildmaker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
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
				bytes[i++] = (byte)(Byte.MIN_VALUE + c.getOrdinal());
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
			
			path = new Stack<Constellation>();
		}
		
		public void run() {
			Constellation stepStella = null;
			do {
				stepStella = nextConstellation(stepStella);
				
				// If there is no next valid step, return to the previous step
				if (stepStella == null) {
					stepStella = path.pop();
					
					// If the popped constellation must be removed
					if (build.remove(stepStella)) {
						buildStars -= stepStella.numStars();
						buildValue -= constellationValues.get(stepStella.getOrdinal());
						buildAffinities.subtract(stepStella.getReward());
					}
					// If the popped constellation must be added back on
					else {
						build.add(stepStella);
						buildStars += stepStella.numStars();
						buildValue += constellationValues.get(stepStella.getOrdinal());
						buildAffinities.add(stepStella.getReward());
					}
				}
				// Else, proceed to the next step
				else {
					// If the next step is a removal
					if (build.remove(stepStella)) {
						BuildBytes buildBytes = new BuildBytes(build);
						
						// If the new build can be added to the visited builds set
						if (visitedBuilds.add(buildBytes)) {
							buildStars -= stepStella.numStars();
							buildValue -= constellationValues.get(stepStella.getOrdinal());
							buildAffinities.subtract(stepStella.getReward());
							
							path.push(stepStella);
							stepStella = null;
							
							// Find the best use for unspent stars
							BuildFinisher.PartialBuild partial =
									buildFinisher.bestPartialBuild(build, buildStars, buildAffinities);
							
							// Give the finished build to TopBuilds
							TopBuilds.getInstance().submit(
									build, partial.getPartials(), buildValue + partial.getValue());
						}
						// If the build has been visited, roll back changes
						else {
							build.add(stepStella);
						}
					}
					// If the next step is an addition
					else {
						build.add(stepStella);
						BuildBytes buildBytes = new BuildBytes(build);
						
						// If the new build can be added to the visited builds set
						if (visitedBuilds.add(buildBytes)) {
							buildStars += stepStella.numStars();
							buildValue += constellationValues.get(stepStella.getOrdinal());
							buildAffinities.add(stepStella.getReward());
							
							path.push(stepStella);
							stepStella = null;
							
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
			} while (path.size() > 0 && continueWalking);
		}
		
		private Constellation nextConstellation(Constellation prevStella) {
			// If there was no previous constellation, pick the very first valid option
			boolean chooseNext = prevStella == null ? true : false;
			
			// If prevStella is not in the build, look for additions
			if (chooseNext || !build.contains(prevStella)) {
				for (Constellation c : sortedConstellations) {
					// If prevConstellation has already been found
					//  If this constellation would fit within the star limit
					//  If this constellation is not in the build yet
					if (chooseNext) {
						if (buildStars + c.numStars() <= Controller.MAX_STARS
								&& !build.contains(c)) {
							return c;
						}
					}
					// If this constellation is prevConstellation
					else if (c == prevStella) {
						// look for and return the next valid constellation
						chooseNext = true;
					}
				}
			}
			
			/* 
			 * If prevConstellation was in the build or no valid additions were
			 * found, look for removals. Search for removals in reverse order
			 * so that lowest value constellations are offered first.
			 */
			ListIterator<Constellation> itr =
					sortedConstellations.listIterator(sortedConstellations.size());
			Constellation c;
			while (itr.hasPrevious()) {
				c = itr.previous();
				
				// If prevConstellation has already been found
				//  If this constellation is already in the build
				//  If this removal would leave the build in a valid state
				if (chooseNext) {
					if (build.contains(c) && isValidRemoval(c)) {
						return c;
					}
				}
				else if (c == prevStella) {
					chooseNext = true;
				}
			}
			
			// There are no valid options left after prevConstellation
			return null;
		}
		
		/*
		 * Returns true if the constellation can be removed from the current
		 * build while leaving enough affinity for every other constellation in
		 * the build. Returns false otherwise.
		 */
		private boolean isValidRemoval(Constellation c) {
			AffinityValues aff = buildAffinities.minus(c.getReward());
			return allAvailable(build, aff);
		}
	}
	
	// Resources shared between walker tasks
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
