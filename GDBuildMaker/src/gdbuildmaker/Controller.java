package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Controller {

	public static final int MAX_STARS = 55;
	public static final int BUILDWALKER_THREADS = 2;
	
	private List<Constellation> constellations;
	
	private ConstellationLoader loader;
	private BuildWalker buildWalker;
	private TopBuilds topBuilds;
	
	public Controller() {
		constellations = new ArrayList<Constellation>();
	}
	
	public void loadConstellations(String dir) {
		loader = new ConstellationLoader();
		constellations = loader.loadConstellations(dir);
	}
	
	public List<String> getLoaderErrors() {
		return loader.getErrors();
	}
	
	public List<Constellation> getConstellations() {
		return new ArrayList<Constellation>(constellations);
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
		final Map<Constellation, Double> constellationValues =
				new HashMap<Constellation, Double>();
		for (Constellation c : constellations) {
			constellationValues.put(c, constellationValue(c, effectWeights));
		}
		
		final Map<Star, Double> starValues = new HashMap<Star, Double>();
		for (Constellation c : constellations) {
			for (Star s : c.getStars()) {
				starValues.put(s, starValue(s, effectWeights));
			}
		}

		topBuilds = new TopBuilds();
		buildWalker = new BuildWalker(constellationValues, starValues, topBuilds);
		buildWalker.start(BUILDWALKER_THREADS);
	}
	
	public void stop() {
		buildWalker.stop();
	}
	
	public Integer getBuildsVisited() {
		return buildWalker.getBuildsVisited();
	}
	
	public List<Build> getTopBuilds() {
		return topBuilds.getBuilds();
	}
	
	private Double starValue(Star star, Map<String, Double> effectWeights) {
		double value = 0.0;
		
		for (String effect : effectWeights.keySet()) {
			value += star.effect(effect) * effectWeights.get(effect);
		}
		
		return value;
	}
	
	private Double constellationValue(Constellation constellation, Map<String, Double> effectWeights) {
		double value = 0.0;
		
		for (Star star : constellation.getStars()) {
			value += starValue(star, effectWeights);
		}
		
		return value;
	}
}
