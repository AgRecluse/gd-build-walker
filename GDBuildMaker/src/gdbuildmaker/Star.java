package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Star {
	public static class Builder {
		private Map<String, Double> effects;
		private List<Star> children;
		private int ordinal;
		
		public Builder() {
			effects = new HashMap<String, Double>();
			children = new ArrayList<Star>();
		}
		
		public Builder addEffect(String text, double value) {
			effects.put(text,  value);
			return this;
		}
		
		public Builder addChild(Star child) {
			children.add(child);
			return this;
		}
		
		public Builder ordinal(int ordinal) {
			this.ordinal = ordinal;
			return this;
		}
		
		public Star build() {
			return new Star(this);
		}
	}
	
	private final Map<String, Double> effects;
	private final List<Star> children;
	private final int ordinal;
	
	private final int hashCode;
	
	private Star(Builder builder) {
		this.effects = Collections.unmodifiableMap(builder.effects);
		this.children = Collections.unmodifiableList(builder.children);
		this.ordinal = builder.ordinal;
		
		hashCode = super.hashCode();
	}
	
	public Map<String, Double> getEffects() {return this.effects; }
	public List<Star> getChildren() {return this.children; }
	public int getOrdinal() {return this.ordinal; }
	
	public double effect(String text) {
		if(effects.containsKey(text)) {
			return effects.get(text);
		}
		return 0.0;
	}
	
	public int hashCode() {
		return hashCode;
	}
}
