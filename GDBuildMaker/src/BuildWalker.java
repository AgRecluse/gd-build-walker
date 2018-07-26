import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

public class BuildWalker {
	private static final class BuildBytes {
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
	
	// Constellation/Star data that more methods need
	private List<Constellation> sortedConstellations;
	
	private BuildFinisher buildFinisher;
	private TopBuilds topBuilds;
	
	// Build variables represent the state of the build at each step in the walk
	private SortedSet<Constellation> build;
	private int buildStars;
	private double buildValue;
	private AffinityValues buildAffinities;
	
	// Options for the next step in the walk
	Stack<Constellation> options; 
	
	public BuildWalker() {
	}
	
	public void walkBuilds(
			List<Constellation> sortedConstellations,
			List<Double> constellationValues,
			Map<Star, Double> starValues) {
		
		this.sortedConstellations = sortedConstellations;
		
		buildFinisher = new BuildFinisher(sortedConstellations, starValues);
		topBuilds = new TopBuilds(Main.TOP_BUILDS);
		
		build = new TreeSet<Constellation>(new Comparator<Constellation>() {
			public int compare(Constellation c1, Constellation c2) {
				return c1.getOrdinal() - c2.getOrdinal();
			}
		});
		buildStars = 0;
		buildValue = 0.0;
		buildAffinities = new AffinityValues();
		
		// All builds visited
		Set<BuildBytes> visitedBuilds = new HashSet<BuildBytes>();
		
		// The history of choices the walk has taken to get to the current step
		Stack<Constellation> path = new Stack<Constellation>();

		options = new Stack<Constellation>();
		pickOptions();
		
		long lastPrint = 0;
		int lastVBSize = 0;
		long printTimer = 3000;
		while (options.size() > 0 || path.size() > 0) {
			// TODO: Print better? Find some better way/place to report results.
			if (lastPrint + printTimer < System.currentTimeMillis()) {
				System.out.println(String.format("Working: %.2f-%s", buildValue, build));
				System.out.println("Total builds: " + visitedBuilds.size());
				System.out.println("New builds: " + (visitedBuilds.size()-lastVBSize));
				System.out.println(topBuilds.buildsString());
				lastPrint = System.currentTimeMillis();
				lastVBSize = visitedBuilds.size();
			}
			
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
						topBuilds.submit(build, partial.getPartials(), buildValue + partial.getValue());
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
						topBuilds.submit(build, partial.getPartials(), buildValue + partial.getValue());
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
	 * Fills the options stack with an ordered list of valid constellation
	 *  selections for the next step in the walk. Removals are on the bottom
	 *  of the options stack, with lowest value removals on top. Additions are
	 *  on top of the options stack, with highest value additions on top.
	 * WARNING: For removals, pickOptions() does NOT check if the resulting
	 *  build will be valid.
	 * Uses: sortedConstellations, build, buildStars, buildAffinities, options
	 * 
	 * @return void
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
				} else if (buildStars + constellation.numStars() <= Main.MAX_STARS) {
					options.add(constellation);
				}
			}
		}
	}
	
	private static boolean allAvailable(Iterable<Constellation> constellations, AffinityValues av) {
		for (Constellation c : constellations) {
			if (!(c.isAvailableWith(av))) return false;
		}
		return true;
	}
}