import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
	private static final String GD_WORKING_DIRECTORY =
			"C:\\Games\\steamapps\\common\\Grim Dawn\\working";
	public static final int MAX_STARS = 55;
	public static final int TOP_BUILDS = 32;
	
	public static double constellationValue(Constellation constellation) {
		Double value = 0.0;
		for(Star star : constellation.getStars()) {
			value += starValue(star, constellation);
		}
		value += constellation.getReward(Affinity.ASCENDANT);
		value += constellation.getReward(Affinity.PRIMORDIAL);
		value += constellation.getReward(Affinity.CHAOS);
		return value;
	}
	
	@SuppressWarnings("unused")
	public static double starValue(Star star, Constellation constellation) {
		double oa = star.effect("characterOffensiveAbility");
		double oaMod = star.effect("characterOffensiveAbilityModifier");
		double da = star.effect("characterDefensiveAbility");
		double daMod = star.effect("characterDefensiveAbilityModifier");
		double cunning = star.effect("characterDexterity");
		double cunningMod = star.effect("characterDexterityModifier");
		double physique = star.effect("characterStrength");
		double physiqueMod = star.effect("characterStrengthModifier");
		
		double lifeRegen = star.effect("characterLifeRegen");
		double lifeRegenMod = star.effect("characterLifeRegenModifier");
		
		double lifeSteal = star.effect("offensiveLifeLeechMin");
		
		double livingShadow = star.effect("Unknown Soldier - Living Shadow");
		double spearOfHeavens = star.effect("Spear of the Heavens - Spear of the Heavens");
		double fistOfVire = star.effect("Vire - Fist of Vire");
		
		double oaValue = oa + (cunning/2) + (oaMod*30) + (cunningMod*15);
		double daValue = da + (physique/2) + (daMod*30) + (physiqueMod*15);
		
		double starValue = 0.0;
		starValue += 100*lifeSteal + 1000*spearOfHeavens + 1000*fistOfVire;
		starValue += oaValue + daValue;
		
		if (constellation.getName().equals("Kraken")) {
			starValue += 200;
		}
		
		return starValue;
	}
	
	public static void printConstellations(Iterable<Constellation> constellations2) {
		for(Constellation constellation : constellations2) {
			System.out.println(constellation.getOrdinal()
					+ ":" + constellation.getName());
			
			List<Star> stars = constellation.getStars();
			for(Star star : constellation.getStars()) {
				System.out.print("  Star: " + stars.indexOf(star));
				System.out.print(" Children: ");
				for(Star child : star.getChildren()) {
					System.out.print(stars.indexOf(child) + " ");
				}
				System.out.println();
				for(Map.Entry<String, Double> effect : star.getEffects().entrySet()) {
					System.out.println("    " + effect.getKey() + ": " + effect.getValue());
				}
			}
		}
	}

	public static void main(String[] args) {
		Main main = new Main();
		main.run();
	}
	
	private final List<Constellation> constellations;
	
	public Main() {
		ConstellationLoader loader = new ConstellationLoader();
		constellations = loader.loadConstellations(GD_WORKING_DIRECTORY);
		
		printConstellations(constellations);
	}
	
	public void run() {
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
		
		for (Constellation constellation : sortedConstellations) {
			System.out.println(constellation.getName() + ": "
					+ constellationValues.get(constellation.getOrdinal()));
		}
		
		BuildWalker bw = new BuildWalker();
		bw.walkBuilds(sortedConstellations, constellationValues, starValues);
	}
}
