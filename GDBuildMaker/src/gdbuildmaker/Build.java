package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Build implements Comparable<Build> {
	private Collection<Constellation> constellations;
	private Map<Constellation, Integer> partials;
	private Double value;
	
	Build(Collection<Constellation> constellations,
			Map<Constellation, Integer> partials, double value) {
		this.constellations = constellations;
		this.partials = partials;
		this.value = value;
	}
	
	public int compareTo(Build o) {
		return this.value.compareTo(o.value);
	}
	
	public Collection<Constellation> getConstellations() {
		return new ArrayList<Constellation>(constellations);
	}
	
	public Map<Constellation, Integer> getPartials() {
		return new HashMap<Constellation, Integer>(partials);
	}
	
	public Double getValue() {
		return value;
	}
	
	public String toString() {
		return String.format("%.2f-%s%s", value, constellations, partials);
	}
}
