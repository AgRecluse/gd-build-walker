package gdbuildmaker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Main {
	private static final String GD_WORKING_DIRECTORY =
			"C:\\Games\\steamapps\\common\\Grim Dawn\\working";
	public static final int MAX_STARS = 55;
	public static final int TOP_BUILDS = 32;
	public static final int THREADS = 2;
	public static final int PRINT_TIMER = 3000;
	
	public static double constellationValue(Constellation constellation) {
		Double value = 0.0;
		for(Star star : constellation.getStars()) {
			value += starValue(star, constellation);
		}
//		value += constellation.getReward(Affinity.ASCENDANT);
//		value += constellation.getReward(Affinity.PRIMORDIAL);
//		value += constellation.getReward(Affinity.CHAOS);
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
		
		double reflection = star.effect("defensiveReflect");
		
		double dodge = star.effect("characterDodgePercent");
		double deflect = star.effect("characterDeflectProjectile");
		
		double elementalMod = star.effect("offensiveElementalModifier");
		double coldMod = star.effect("offensiveColdModifier");
		double frostburnMod = star.effect("offensiveSlowColdModifier");
		
		double vitalityMod = star.effect("offensiveLifeModifier");
		
		double livingShadow = star.effect("Unknown Soldier - Living Shadow");
		double spearOfHeavens = star.effect("Spear of the Heavens - Spear of the Heavens");
		double fistOfVire = star.effect("Vire - Fist of Vire");
		double shieldWall = star.effect("Targo the Builder - Shield Wall");
		double elementalStorm = star.effect("Rhowan's Crown - Elemental Storm");
		double blizzard = star.effect("Amatok Spirit of Winter - Blizzard");
		double rumor = star.effect("Murmur - Rumor");
		double whirlpool = star.effect("Leviathan - Whirlpool");
		double markOfRattosh = star.effect("Rattosh - Mark of Rattosh");
		
		double oaValue = oa + (cunning/2) + (oaMod*20) + (cunningMod*10);
		double daValue = da + (physique/2) + (daMod*20) + (physiqueMod*10);
		
		double starValue = 0.0;
		
		// Retaliation build
//		Pattern retaliationPattern = Pattern.compile("retaliation");
//		for (Map.Entry<String, Double> effect : star.getEffects().entrySet()) {
//			if (retaliationPattern.matcher(effect.getKey().toLowerCase()).find()) {
//				starValue += effect.getValue();
//			}
//		}
//		starValue += 100*reflection;
//		starValue += 1000*shieldWall;
		
		// 2-handed counter-attacking build
//		starValue += 100*lifeSteal + 1000*spearOfHeavens + 1000*fistOfVire;
//		starValue += oaValue + daValue;
//		
//		if (constellation.getName().equals("Kraken")) {
//			starValue += 200;
//		}
		
		// Living Shadow build with OA/DA and life steal
//		starValue += 1000*livingShadow;
//		starValue += 20*lifeSteal;
//		starValue += oaValue + 1.1*daValue;
//		if (constellation.getName().equals("Rhowan's Scepter")) return 0;
//		if (constellation.getName().equals("Hydra")) return 0;
		
		// Cold build with dodge chance
//		starValue += 1000*(elementalStorm + whirlpool + rumor);
//		starValue += 100*(dodge + deflect);
//		starValue += elementalMod + coldMod + frostburnMod;
		
		// Blood knight build
		if (constellation.getName().equals("Oklaine's Lantern")) return 0;
		if (constellation.getName().equals("Hydra")) return 0;
		starValue += 100000*(markOfRattosh);
		starValue += 10*(oaValue + daValue);
		starValue += vitalityMod;
		
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
		
		BuildWalker.setup(sortedConstellations, constellationValues, starValues);
		
		for (int i = 0; i < THREADS; i++) {
			new Thread() {
				public void run() {
					new BuildWalker().walkBuilds();
				}
			}.start();
		}
		
		while (true) {
			try {Thread.sleep(PRINT_TIMER);} catch (InterruptedException e) {}
			report();
		}
	}
	
	private static int lastTotalBuilds = 0;
	private static long startTime = System.currentTimeMillis();
	public static void report() {
		long runTime = (System.currentTimeMillis()-startTime)/1000;
		int totalBuilds = BuildWalker.totalBuilds();
		int walkRate = totalBuilds - lastTotalBuilds;
		int bps = (int)((walkRate)/(PRINT_TIMER/1000.0));
		
		StringBuilder builder = new StringBuilder();
		builder.append("      Run Time: " + runTime + "\n");
		builder.append("Visited Builds: " + totalBuilds + "\n");
		builder.append("    New Builds: " + walkRate + "\n");
		builder.append(" Builds/Second: " + bps + "\n");
		builder.append(TopBuilds.getInstance().buildsString() + "\n");
		
		System.out.println(builder.toString());
		
		lastTotalBuilds = totalBuilds;
	}
}
