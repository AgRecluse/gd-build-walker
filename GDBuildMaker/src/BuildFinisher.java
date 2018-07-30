import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BuildFinisher {
	public final class PartialBuild {
		private final Double value;
		private final Map<Constellation, Integer> partials;
		
		private PartialBuild(Double v, Map<Constellation, Integer> ps) {
			value = v;
			partials = Collections.unmodifiableMap(ps);
		}
		
		public Double getValue() {
			return value;
		}
		
		public Map<Constellation, Integer> getPartials() {
			return partials;
		}
	}
	
	private final class PartialConstellation {
		private final Constellation constellation;
		private final Star headStar;
		private final Collection<Star> newHeads;
		private final int starsUsed;
		private final double avgValue;
		
		private PartialConstellation(Constellation constellation, Star headStar,
				Collection<Star> newHeads, int starsUsed, double avgValue) {
			this.constellation = constellation;
			this.headStar = headStar;
			this.newHeads = newHeads;
			this.starsUsed = starsUsed;
			this.avgValue = avgValue;
		}
	}
	
	private static List<Constellation> constellations;
	private static Map<Star, Double> starValues;
	
	// Cached values for bestPartialConstellation()
	private static Map<Pair<Star, Integer>, PartialConstellation> cachedPartialConstellations;
	
	public static void setup(List<Constellation> constellations, Map<Star, Double> starValues) {
		BuildFinisher.constellations = constellations;
		BuildFinisher.starValues = starValues;
		cachedPartialConstellations =
				new ConcurrentHashMap<Pair<Star, Integer>, PartialConstellation>();
	}
	
	// Best average sub-constellations
	private List<PartialConstellation> partialConstellations;
	
	public BuildFinisher() {
		partialConstellations = new ArrayList<PartialConstellation>();
	}
	
	public PartialBuild bestPartialBuild(Collection<Constellation> build,
			int buildStars, AffinityValues buildAffinites) {
		
		int freeStars = Main.MAX_STARS - buildStars;
		
		// If no stars are left to pick, best build has value 0 and no stars
		if (freeStars <= 0) {
			return new PartialBuild(0.0, new HashMap<Constellation, Integer>());
		}

		// Get ready for a new run
		Double totalValue = 0.0;
		Map<Constellation, Integer> constellationStars = new HashMap<Constellation, Integer>();
		partialConstellations.clear();
		
		// Make the initial working list of best partials
		for (Constellation c : constellations) {
			// If the constellation is available and has not been selected in the build
			if (c.isAvailableWith(buildAffinites) && !(build.contains(c))) {
				partialConstellations.add(bestPartialConstellation(c, c.headStar(), freeStars));
			}
		}
		
		if (partialConstellations.size() <= 0 ) {
			return new PartialBuild(0.0, new HashMap<Constellation, Integer>());
		}
		
		while (freeStars > 0 && partialConstellations.size() > 0) {
			PartialConstellation bestPC = partialConstellations.get(0);
			for (int i = 0; i < partialConstellations.size(); i++) {
				// Make sure the partials all fit within the star allowance
				PartialConstellation pc = partialConstellations.get(i); 
				if (pc.starsUsed > freeStars) {
					partialConstellations.set(i,
							bestPartialConstellation(
									pc.constellation,
									pc.headStar,
									freeStars));
				}
				// Find the partial with the best average
				if (partialConstellations.get(i).avgValue > bestPC.avgValue) {
					bestPC = partialConstellations.get(i);
				}
			}
			// If there is no value left in the partials, just stop
			if (bestPC.avgValue <= 0) { break; }

			// Remove the best partial constellation from the list
			partialConstellations.remove(bestPC);
			
			// Add the stars used to the constellation's star total
			Integer count = constellationStars.get(bestPC.constellation);
			constellationStars.put(
					bestPC.constellation,
					bestPC.starsUsed + (count == null ? 0 : count));
			
			// Keep track of total value obtained and free stars left to use
			totalValue += bestPC.avgValue * bestPC.starsUsed;
			freeStars -= bestPC.starsUsed;
			
			// Add unused sub-constellations back into the partial constellation list
			if (freeStars > 0) {
				for (Star star : bestPC.newHeads) {
					partialConstellations.add(
							bestPartialConstellation(bestPC.constellation, star, freeStars));
				}
			}
		}
		
		return new PartialBuild(totalValue, constellationStars);
	}
	
	public PartialConstellation bestPartialConstellation(
			Constellation constellation,
			Star startingStar,
			int allowedStars) {
		
		int newAllowedStars =
				allowedStars < constellation.numStars() ? allowedStars : constellation.numStars();
		
		Pair<Star, Integer> pair = Pair.of(startingStar, newAllowedStars);
		
		PartialConstellation pc;
		if ((pc = cachedPartialConstellations.get(pair)) != null) {
			return pc;
		}
		
		pc = bestPartialConstellation(
				startingStar,
				newAllowedStars,
				0.0,
				0);
		
		// Put the constellation into the PartialConstellation
		pc = new PartialConstellation(
				constellation,
				pc.headStar,
				pc.newHeads,
				pc.starsUsed,
				pc.avgValue);
		
		cachedPartialConstellations.put(pair, pc);
		return pc;
	}
	
	private PartialConstellation bestPartialConstellation(
			Star headStar, int allowedStars, double runningAvg, int prevStars) {
		
		if (allowedStars == 1) {
			return new PartialConstellation(
					null, headStar, headStar.getChildren(), 1, starValues.get(headStar));
		}
		
		Double stepAvg = runningAverage(runningAvg, prevStars, starValues.get(headStar), 1);
		
		List<PartialConstellation> subResults = new ArrayList<PartialConstellation>();
		List<Star> newHeads = new ArrayList<Star>();
		int starsUsed = 1;
		for (Star child : headStar.getChildren()) {
			PartialConstellation subResult = 
					bestPartialConstellation(child, allowedStars-1, stepAvg, prevStars+1);
			
			if (subResult.avgValue >= stepAvg) {
				subResults.add(subResult);
				starsUsed += subResult.starsUsed;
			} else {
				newHeads.add(child);
			}
		}
		
		// Put the average of the best sub-tree (from this head star and on) in here
		double bestAvg;
		
		// If it would use too many stars to include all sub-results
		if (starsUsed > allowedStars) {
			// TODO: make a better algorithm for this. Until then, just take the best sub-result
			
			// Pick the best sub-result, add the others' heads to the new heads list
			PartialConstellation bestPC = null;
			for (PartialConstellation pc : subResults) {
				if (bestPC == null) {
					bestPC = pc;
				} else if (pc.avgValue > bestPC.avgValue) {
					newHeads.add(bestPC.headStar);
					bestPC = pc;
				} else {
					newHeads.add(pc.headStar);
				}
			}
			newHeads.addAll(bestPC.newHeads);
			
			// Calculate the total average and count all stars used
			bestAvg = runningAverage(starValues.get(headStar), 1, bestPC.avgValue, bestPC.starsUsed);
			starsUsed = 1 + bestPC.starsUsed;
		}
		// If all sub-results can be included
		else {
			// Calculate the total average, gather all new heads
			starsUsed = 1;
			bestAvg = starValues.get(headStar);
			for (PartialConstellation pc : subResults) {
				bestAvg = runningAverage(bestAvg, starsUsed, pc.avgValue, pc.starsUsed);
				starsUsed += pc.starsUsed;
				newHeads.addAll(pc.newHeads);
			}
		}
		
		return new PartialConstellation(null, headStar, newHeads, starsUsed, bestAvg);
	}
	
	private static double runningAverage(double lAvg, int lCount, double rAvg, int rCount) {
		return lAvg*lCount/(lCount+rCount) + rAvg/(lCount+rCount);
	}
}
