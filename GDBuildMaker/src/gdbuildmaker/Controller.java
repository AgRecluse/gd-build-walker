package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Controller {

	public static final int MAX_STARS = 55;
	public static final int BUILDWALKER_THREADS = 2;
	
	private List<Constellation> constellations;
	private Map<String, Double> effectWeights;
	
	private BuildWalker buildWalker;
	
	public Controller() {
		constellations = new ArrayList<Constellation>();
	}
	
	public void loadConstellations(String dir) {
		ConstellationLoader loader = new ConstellationLoader();
		constellations = loader.loadConstellations(dir);
	}
	
	public List<String> getEffectList() {
		List<String> effects = new ArrayList<String>();
		for (Constellation c : constellations) {
			for (Star s : c.getStars()) {
				for (String effect : s.getEffects().keySet()) {
					if (!effects.contains(effect)) {
						effects.add(effect);
					}
				}
			}
		}
		Collections.sort(effects);
		return effects;
	}

	public void start(Map<String, Double> effectWeights) {
		this.effectWeights = effectWeights;
		
		final List<Double> constellationValues =
				new ArrayList<Double>(constellations.size());
		for (Constellation constellation : constellations) {
			constellationValues.add(constellationValue(constellation));
		}
		
		final List<Constellation> sortedConstellations =
				new ArrayList<Constellation>(constellations);
		Collections.sort(sortedConstellations, new Comparator<Constellation>() {
			public int compare(Constellation c1, Constellation c2) {
				return constellationValues.get(c1.getOrdinal())
						.compareTo(constellationValues.get(c2.getOrdinal()));
			}
		});
		
		final Map<Star, Double> starValues = new HashMap<Star, Double>();
		for (Constellation c : constellations) {
			for (Star s : c.getStars()) {
				starValues.put(s, starValue(s, c));
			}
		}

		TopBuilds.reset();
		buildWalker = new BuildWalker(sortedConstellations, constellationValues, starValues);
		buildWalker.start(BUILDWALKER_THREADS);
	}
	
	public void stop() {
		buildWalker.stop();
	}
	
	public Integer getBuildsVisited() {
		return buildWalker.getBuildsVisited();
	}
	
	public List<Build> getTopBuilds() {
		return TopBuilds.getInstance().getBuilds();
	}
	
	private Double starValue(Star star, Constellation constellation) {
		double value = 0.0;
		
		for (String effect : effectWeights.keySet()) {
			value += star.effect(effect) * effectWeights.get(effect);
		}
		
		return value;
	}
	
	private Double constellationValue(Constellation constellation) {
		double value = 0.0;
		
		for (Star star : constellation.getStars()) {
			value += starValue(star, constellation);
		}
		
		return value;
	}
}
