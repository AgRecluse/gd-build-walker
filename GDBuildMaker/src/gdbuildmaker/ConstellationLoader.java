package gdbuildmaker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstellationLoader {
	private static final String MODS_DIR = "mods";
	private static final String BASE_DIR = "database";
	private static final String CONSTELLATION_DIR =
			"records" + File.separator + "ui" + File.separator +
			"skills" + File.separator + "devotion" + File.separator +
			"constellations";
	
	private static final class Patterns {
		private static final Pattern constellationName = Pattern.compile("FileDescription");
		private static final Pattern rewardValue = Pattern.compile("affinityGiven([0-9])");
		private static final Pattern rewardType = Pattern.compile("affinityGivenName([0-9])");
		private static final Pattern requirementValue = Pattern.compile("affinityRequired([0-9])");
		private static final Pattern requirementType = Pattern.compile("affinityRequiredName([0-9])");
		private static final Pattern starFilename = Pattern.compile("devotionButton([0-9])");
		private static final Pattern starLink = Pattern.compile("devotionLinks([0-9])");
		
		private static final Pattern skillFilename = Pattern.compile("skillName");
		private static final Pattern skillFile = Pattern.compile("_skill");
	}
	
	private List<String> errors;
	
	public ConstellationLoader() {
		errors = new ArrayList<String>();
	}
	
	public List<String> getErrors() {
		return new ArrayList<String>(errors);
	}
	
	public List<Constellation> loadConstellations(String workingDirectory) {
		errors.clear();
		List<Constellation> constellations = new ArrayList<Constellation>();
		
		for(File baseDirectory : baseDirectories(workingDirectory)) {
			File constellationDir = new File(baseDirectory, CONSTELLATION_DIR);
			if(!constellationDir.isDirectory()) {
				errors.add("No constellation info in: " + constellationDir.getPath());
				continue;
			}
			
			for(String constellationPath : constellationDir.list()) {
				Constellation.Builder newCBuilder = 
						loadConstellationBuilder(
								baseDirectory,
								new File(constellationDir, constellationPath));
				if(newCBuilder != null) {
					newCBuilder.ordinal(constellations.size());
					constellations.add(newCBuilder.build());
				}
			}
		}
		
		return constellations;
	}
	
	/**
	 * Find all directories to pull constellation and star information from.
	 * 
	 * @param workingDirectory: top level directory to look in
	 * @return
	 */
	private List<File> baseDirectories(String workingDirectory) {
		File modsDir = new File(workingDirectory, MODS_DIR);
		
		// Create a list of base paths for mods, and add the base game's data to it, too
		List<File> baseDirs = new ArrayList<File>();
		baseDirs.add(new File(workingDirectory, BASE_DIR));
		
		String[] mods = modsDir.list();
		if(mods == null) {
			errors.add("Mods directory not found at: " + modsDir.getPath());
			return baseDirs;
		}
		
		// Fill basePaths with mod directories in which to search for constellations and stars
		for(String mod : mods) {
			File modBase = new File(new File(modsDir, mod), BASE_DIR);
			if(modBase.isDirectory()) {
				baseDirs.add(modBase);
			}
		}
		
		return baseDirs;
	}
	
	private Constellation.Builder loadConstellationBuilder(File baseDir, File constellationFile) {
		List<Integer> rewardValues = new ArrayList<Integer>();
		List<Affinity> rewardTypes = new ArrayList<Affinity>();
		List<Integer> requirementValues = new ArrayList<Integer>();
		List<Affinity> requirementTypes = new ArrayList<Affinity>();
		List<Star.Builder> starBuilders = new ArrayList<Star.Builder>();
		List<Integer> starLinks = new ArrayList<Integer>();
		starLinks.add(-1); // Head star has no parent and is not recorded in constellation files

		Constellation.Builder constellationBuilder = new Constellation.Builder();
		
		try(BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(constellationFile)))) {
			
			String line;
			while((line = br.readLine()) != null) {
				String[] split = line.split(",");
				Matcher m;
				
				m = Patterns.constellationName.matcher(split[0]);
				if(m.matches()) {
					constellationBuilder.name(split[1]);
				}
				m = Patterns.rewardValue.matcher(split[0]);
				if(m.matches()) {
					rewardValues.add(Integer.parseInt(split[1]));
				}
				m = Patterns.rewardType.matcher(split[0]);
				if(m.matches()) {
					rewardTypes.add(Affinity.valueOf(split[1].toUpperCase()));
				}
				m = Patterns.requirementValue.matcher(split[0]);
				if(m.matches()) {
					requirementValues.add(Integer.parseInt(split[1]));
				}
				m = Patterns.requirementType.matcher(split[0]);
				if(m.matches()) {
					requirementTypes.add(Affinity.valueOf(split[1].toUpperCase()));
				}
				m = Patterns.starFilename.matcher(split[0]);
				if(m.matches()) {
					starBuilders.add(loadStarBuilder(baseDir, split[1]));
				}
				m = Patterns.starLink.matcher(split[0]);
				if(m.matches()) {
					starLinks.add(Integer.parseInt(split[1])-1);
				}
			}
		} catch(IOException e) {
			errors.add("Unable to read constellation file: " + constellationFile.toPath());
		}
		
		// If no stars were found in the file, don't return a builder
		if(starBuilders.isEmpty()) {return null; }
		
		// Iterate over affinity TYPES, there may be more values than types in the data
		AffinityValues reward = new AffinityValues();
		for(int i = 0; i < rewardTypes.size(); i++) {
			reward.setValue(rewardTypes.get(i), rewardValues.get(i));
		}
		constellationBuilder.reward(reward);
		
		AffinityValues requirement = new AffinityValues();
		for(int i = 0; i < requirementTypes.size(); i++) {
			requirement.setValue(requirementTypes.get(i), requirementValues.get(i));
		}
		constellationBuilder.requirement(requirement);
		
		// Build stars in reverse to make the tree
		for(int i = starBuilders.size()-1; i > 0; i--) {
			Star star = starBuilders.get(i).build();
			starBuilders.get(starLinks.get(i)).addChild(star);
			constellationBuilder.addStar(0, star);
		}
		constellationBuilder.addStar(0, starBuilders.get(0).build());
		
		// Special case for 'Crossroads' constellations
	    if (constellationBuilder.name.equals("Crossroads")) {
	    	// Change the name so we can tell them apart
	        constellationBuilder.name("Crossroad" + rewardTypes.get(0).name().charAt(0));
	    }
		
		return constellationBuilder;
	}
	
	private Star.Builder loadStarBuilder(File baseDir, String starPath) {
		File starUIFile = new File(baseDir, starPath);
		File starSkillFile = null;
		try(BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(starUIFile)))) {
			String line;
			while((line = br.readLine()) != null) {
				String[] split = line.split(",");
				
				Matcher m = Patterns.skillFilename.matcher(split[0]);
				if(m.matches()) {
					starSkillFile = new File(baseDir, split[1]);
					break;
				}
			}
			
		} catch(IOException e) {
			errors.add("Unable to read star UI file: " + starUIFile);
			return null;
		}

		Star.Builder starBuilder = new Star.Builder();
		
		try(BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(starSkillFile)))) {
			// If the skill file indicator is in the star file's name
			if(Patterns.skillFile.matcher(starSkillFile.getName()).find()) {
				String line;
				while((line = br.readLine()) != null) {
					String[] split = line.split(",");
					if(Patterns.constellationName.matcher(split[0]).matches()) {
						starBuilder.addEffect(split[1], 1.0);
					}
				}
			} else {
				String line;
				while((line = br.readLine()) != null) {
					String[] split = line.split(",");
					try {
						Double value = Double.parseDouble(split[1]);
						if(value != 0.0 && !split[0].equals("skillMaxLevel")) {
							starBuilder.addEffect(split[0], value);
						}
					} catch(NumberFormatException e) {}
				}
			}
		} catch(IOException e) {
			errors.add("Unable to read star skill file: " + starUIFile);
			return null;
		}
		
		return starBuilder;
	}
}
