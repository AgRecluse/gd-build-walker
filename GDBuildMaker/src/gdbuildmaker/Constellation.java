package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Constellation {
	
	public static class Builder {
		public String name;
		private int ordinal;
		private List<Star> stars;
		private AffinityValues requirement;
		private AffinityValues reward;
		
		public Builder() {
			this.stars = new ArrayList<Star>();
		}
		
		public Builder name(String name) {
			this.name = name;
			return this;
		}
		
		public Builder ordinal(int ordinal) {
			this.ordinal = ordinal;
			return this;
		}
		
		public Builder addStar(Star star) {
			this.stars.add(star);
			return this;
		}
		
		public Builder addStar(int pos, Star star) {
			this.stars.add(pos, star);
			return this;
		}
		
		public Builder requirement(AffinityValues requirement) {
			this.requirement = requirement;
			return this;
		}
		
		public Builder reward(AffinityValues reward) {
			this.reward = reward;
			return this;
		}
		
		public Constellation build() {
			return new Constellation(this);
		}
	}

	private final String name;
	private final int ordinal;
	private final List<Star> stars;
	private final AffinityValues requirement;
	private final AffinityValues reward;
	private final Map<String, Double> effects;
	
	public Constellation(Builder builder) {
		this.name = builder.name;
		this.ordinal = builder.ordinal;
		this.stars = Collections.unmodifiableList(builder.stars);
		this.requirement = builder.requirement;
		this.reward = builder.reward;
		
		Map<String, Double> effects = new HashMap<String, Double>();
		for(Star star : this.stars) {
			for(String text : star.getEffects().keySet()) {
				if(effects.containsKey(text)) {
					effects.put(text, effects.get(text) + star.effect(text));
				} else {
					effects.put(text, star.effect(text));
				}
			}
		}
		this.effects = Collections.unmodifiableMap(effects);
	}
	
	public String getName() { return this.name; }
	public int getOrdinal() { return this.ordinal; }
	public List<Star> getStars() { return this.stars; }
	public AffinityValues getRequirement() { return this.requirement; }
	public AffinityValues getReward() { return this.reward; }
	public int getReward(Affinity a) { return this.reward.getValue(a); }
	
	public int numStars() {
		return stars.size();
	}
	
	public Star headStar() {
		return stars.get(0);
	}
	
	public boolean isAvailableWith(AffinityValues av) {
		return av.fullfills(this.requirement);
	}
	
	public double effect(String text) {
		if(effects.containsKey(text)) {
			return effects.get(text);
		}
		return 0.0;
	}
	
	public String toString() {
		return name;
	}
}
