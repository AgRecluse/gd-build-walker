package gdbuildmaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;

public class Main extends Application {
	
	Controller model;
	TabPane tabPane;
	Tab loadTab;
	Tab characterTab;
	Tab valueTab;
	Tab runTab;
	Thread uiUpdaterThread;
	
	ObservableList<String> effectsList;
	ObservableList<NameValuePair> effectWeights;
	ObservableList<NameValuePair> characterAttributeValues;
	
	TextField buildsVisitedField;
	TextField buildsPerSecondField;
	TextArea topBuildsField;
	
	private class NameValuePair {
		public String name = null;
		public Double value = 0.0;
	}
	
	private class EffectValueCell extends ListCell<NameValuePair> {
		private HBox cellPane;
		private TextField valueField;
		private ComboBox<String> effectComboBox;
		
		public EffectValueCell() {
			// Text field for setting the value
			valueField = new TextField();
			// Update value on focus change
			valueField.focusedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
					// If the text field had focus, save the value
					if (oldValue) saveWeight();
				}
			});
			// Update value when Enter is pressed
			valueField.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent ae) {
					saveWeight();
				}
			});
			
			// Combo box for setting the bonus type
			effectComboBox = new AutocompleteComboBox(effectsList);
			effectComboBox.setPromptText("effect type");
			effectComboBox.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent ae) {
					EffectValueCell.this.getItem().name = effectComboBox.getValue();
				}
			});
			
			// Button for removing this item from the list
			Button deleteButton = new Button();
			deleteButton.setText("Delete");
			deleteButton.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent ae) {
					EffectValueCell.this.getListView().getItems().remove(
							EffectValueCell.this.getItem());
				}
			});

			cellPane = new HBox(8);
			cellPane.getChildren().add(valueField);
			cellPane.getChildren().add(effectComboBox);
			cellPane.getChildren().add(deleteButton);
		}
		
		/**
		 * Save the weight in the text field to the underlying item. If the
		 * text cannot be converted to a double, set the text to the weight
		 * already in the item.
		 */
		private void saveWeight() {
			try {
				getItem().value = Double.parseDouble(valueField.getText());
			} catch (NumberFormatException e) {}
			valueField.setText(String.valueOf(getItem().value));
		}
		
		@Override
		protected void updateItem(NameValuePair bv, boolean empty) {
			super.updateItem(bv, empty);
			
			if (empty) {
				this.setGraphic(null);
			} else {
				this.setGraphic(cellPane);
				valueField.setText(bv.value.toString());
				effectComboBox.setValue(bv.name);
			}
		}
	}
	
	private class UIUpdaterTask implements Runnable {
		@Override
		public void run() {
			
			try {
				while (true) {
					updateUI();
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
			} catch (NullPointerException e) {
				System.out.print(e.getStackTrace());
			} finally {
				try {
					updateUI();
				} catch (NullPointerException e) {
					System.out.print(e.getStackTrace());
				}
			}
		}
		
		private List<Build> lastTopBuilds = new ArrayList<Build>();
		private int lastBuildsVisited = 0;
		private void updateUI() {
			// Take a snapshot of builds visited, as it changes rapidly
			int newBuildsVisited = model.getBuildsVisited();
			buildsVisitedField.setText(Integer.toString(newBuildsVisited));
			buildsPerSecondField.setText(
					Integer.toString(newBuildsVisited-lastBuildsVisited));
			lastBuildsVisited = newBuildsVisited;
			
			if (!lastTopBuilds.equals(model.getTopBuilds())) {
				StringBuilder builder = new StringBuilder();
				for (Build build : model.getTopBuilds()) {
					builder.append(build.toString() + "\n");
				}
				topBuildsField.setText(builder.toString());
				lastTopBuilds = model.getTopBuilds();
			}
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	public void start(Stage primaryStage) {
		try {
			model = new Controller();
			effectsList = FXCollections.observableArrayList();
			
			loadTab = makeLoadTab();
			characterTab = makeCharacterTab();
			valueTab = makeValueTab();
			runTab = makeRunTab();
			
			tabPane = new TabPane();
			tabPane.getTabs().add(loadTab);
			tabPane.getTabs().add(characterTab);
			tabPane.getTabs().add(valueTab);
			tabPane.getTabs().add(runTab);
			
			Scene scene = new Scene(tabPane, 600, 400);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.setTitle("Grim Dawn Devotion Walker");
			primaryStage.show();
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public Tab makeLoadTab() {
		Tab loadTab = new Tab();
		loadTab.setText("Load Constellations");
		loadTab.closableProperty().set(false);
		
		// Setup instructions
		Text setup1 = new Text("First time setup: extract the Grim Dawn source files.");
		Text setup2 = new Text("Complete the Modding Beginner's Guide I, section 1.2.3 Setting Up Asset Manager:");
		TextField setup3 = new TextField("https://forums.crateentertainment.com/t/script-basics-modding-beginners-guide-i/37525/2");
		setup3.setEditable(false);
		
		VBox setupPane = new VBox(0);
		setupPane.getChildren().add(setup1);
		setupPane.getChildren().add(setup2);
		setupPane.getChildren().add(setup3);
		
		// Label and field for showing loaded constellations
		Text constellationsLabel = new Text("Loaded Constellations");
		TextArea constellationsField = new TextArea();
		constellationsField.editableProperty().set(false);
		VBox constellationsPane = new VBox(0);
		constellationsPane.getChildren().add(constellationsLabel);
		constellationsPane.getChildren().add(constellationsField);
		
		Text workingDirLabel = new Text("Grim Dawn Working Directory");
		TextField workingDirField = new TextField("C:\\Games\\steamapps\\common\\Grim Dawn\\working");
		workingDirField.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent ae) {
				model.loadConstellations(workingDirField.getText());
				effectsList.setAll(model.getEffectList());
				
				StringBuilder builder = new StringBuilder();
				for (Constellation c : model.getConstellations()) {
					builder.append(c.getName() + " ");
					builder.append("Requires: " + c.getRequirement() + " ");
					builder.append("Rewards: " + c.getReward() + "\n");
					int i = 0;
					for (Star s : c.getStars()) {
						builder.append("\tStar " + (++i) + ": ");
						builder.append(s.getEffects().toString() + "\n");
					}
					builder.append("\n");
				}
				for (String str : model.getLoaderErrors()) {
					builder.append(str + "\n");
				}
				constellationsField.setText(builder.toString());
			}
		});
		VBox workingDirPane = new VBox(0);
		workingDirPane.getChildren().add(workingDirLabel);
		workingDirPane.getChildren().add(workingDirField);
		
		// Organize everything in a vertical box
		VBox loadPane = new VBox(8);
		loadPane.getChildren().add(setupPane);
		loadPane.getChildren().add(workingDirPane);
		loadPane.getChildren().add(constellationsPane);
		
		loadTab.setContent(loadPane);
		return loadTab;
	}
	
	public Tab makeCharacterTab() {
		Tab characterTab = new Tab();
		characterTab.setText("Character");
		characterTab.closableProperty().set(false);
		
		characterAttributeValues = FXCollections.observableArrayList();
		
		ListView<NameValuePair> characterEffectsView = new ListView<NameValuePair>();
		characterEffectsView.setItems(characterAttributeValues);
		characterEffectsView.setCellFactory(new Callback<ListView<NameValuePair>, ListCell<NameValuePair>>() {
			@Override public ListCell<NameValuePair> call(ListView<NameValuePair> list) {
				return new EffectValueCell();
			}
		});
		
		Button newAttributeButton = new Button();
		newAttributeButton.setText("New Attribute");
		newAttributeButton.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent ae) {
				characterAttributeValues.add(new NameValuePair());
			}
		});
		
		// Organize everything in a vertical box
		VBox characterPane = new VBox(8);
		characterPane.getChildren().add(newAttributeButton);
		characterPane.getChildren().add(characterEffectsView);
		
		characterTab.setContent(characterPane);
		
		return characterTab;
	}
	
	public Tab makeValueTab() {
		Tab valueTab = new Tab();
		valueTab.setText("Star Value");
		valueTab.closableProperty().set(false);
		
		effectWeights = FXCollections.observableArrayList();
		
		ListView<NameValuePair> effectWeightsView = new ListView<NameValuePair>();
		effectWeightsView.setItems(effectWeights);
		effectWeightsView.setCellFactory(new Callback<ListView<NameValuePair>, ListCell<NameValuePair>>() {
			@Override public ListCell<NameValuePair> call(ListView<NameValuePair> list) {
				return new EffectValueCell();
			}
		});
		
		BorderPane buttonBar = new BorderPane();
		
		Button valueButton = new Button();
		valueButton.setText("New Value");
		valueButton.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent ae) {
				effectWeights.add(new NameValuePair());
			}
		});
		
		Button runButton = new Button();
		runButton.setText("Start Run");
		runButton.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent ae) {
				handleRunButton();
			}
		});
		
		buttonBar.setLeft(valueButton);
		buttonBar.setRight(runButton);

		// Organize everything in a vertical box
		VBox valuePane = new VBox(8);
		valuePane.getChildren().add(buttonBar);
		valuePane.getChildren().add(effectWeightsView);
		
		valueTab.setContent(valuePane);
		return valueTab;
	}
	
	private Tab makeRunTab() {
		Tab runTab = new Tab();
		runTab.setText("Run");
		runTab.setClosable(false);
		
		Text buildsVisitedLabel = new Text("Builds Visited:");
		buildsVisitedField = new TextField();
		buildsVisitedField.editableProperty().set(false);
		
		Text buildsPerSecondLabel = new Text("Builds/sec:");
		buildsPerSecondField = new TextField();
		buildsPerSecondField.editableProperty().set(false);
		
		Button stopButton = new Button();
		stopButton.setText("Stop Run");
		stopButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent ae) {
				handleStopButton();
			}
		});
		
		HBox buildsBar = new HBox(8);
		buildsBar.getChildren().add(buildsVisitedLabel);
		buildsBar.getChildren().add(buildsVisitedField);
		buildsBar.getChildren().add(buildsPerSecondLabel);
		buildsBar.getChildren().add(buildsPerSecondField);
		buildsBar.getChildren().add(stopButton);
		
		topBuildsField = new TextArea();
		topBuildsField.editableProperty().set(false);
		
		// organize everything in a vertical box
		VBox runPane = new VBox(8);
		runPane.getChildren().add(buildsBar);
		runPane.getChildren().add(topBuildsField);
		
		runTab.setContent(runPane);
		return runTab;
	}
	
	private void handleRunButton() {
		// Don't do anything if the build walker is already running
		if (uiUpdaterThread != null) return;
		
		Map<String, Double> starValueMap = new HashMap<String, Double>();
		for (NameValuePair ew : effectWeights) {
			if (ew.name != null) starValueMap.put(ew.name, ew.value);
		}
		
		model.start(starValueMap);

		uiUpdaterThread = new Thread(new UIUpdaterTask());
		uiUpdaterThread.setDaemon(true);
		uiUpdaterThread.start();
		
		tabPane.getSelectionModel().select(runTab);
	}
	
	private void handleStopButton() {
		if (uiUpdaterThread == null) return;
		
		model.stop();
		
		uiUpdaterThread.interrupt();
		try {
			uiUpdaterThread.join();
		} catch (InterruptedException e) {}

		uiUpdaterThread = null;
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
}
