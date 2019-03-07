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
	Tab valueTab;
	Tab runTab;
	Thread uiUpdaterThread;
	
	ObservableList<String> effectsList;
	ObservableList<EffectWeight> effectWeights;
	
	TextField buildsVisitedField;
	TextArea topBuildsField;
	
	private class EffectWeight {
		public String effect = null;
		public Double weight = 0.0;
	}
	
	private class EffectWeightCell extends ListCell<EffectWeight> {
		private HBox cellPane;
		private TextField weightField;
		private ComboBox<String> effectComboBox;
		
		public EffectWeightCell() {
			// Text field for setting the value
			weightField = new TextField();
			// Update weight on focus change
			weightField.focusedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
					// If the text field had focus, save the weight
					if (oldValue) saveWeight();
				}
			});
			// Update weight when Enter is pressed
			weightField.setOnAction(new EventHandler<ActionEvent>() {
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
					EffectWeightCell.this.getItem().effect = effectComboBox.getValue();
				}
			});
			
			// Button for removing this item from the list
			Button deleteButton = new Button();
			deleteButton.setText("Delete");
			deleteButton.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent ae) {
					EffectWeightCell.this.getListView().getItems().remove(
							EffectWeightCell.this.getItem());
				}
			});

			cellPane = new HBox(8);
			cellPane.getChildren().add(weightField);
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
				getItem().weight = Double.parseDouble(weightField.getText());
			} catch (NumberFormatException e) {}
			weightField.setText(String.valueOf(getItem().weight));
		}
		
		@Override
		protected void updateItem(EffectWeight bv, boolean empty) {
			super.updateItem(bv, empty);
			
			if (empty) {
				this.setGraphic(null);
			} else {
				this.setGraphic(cellPane);
				weightField.setText(bv.weight.toString());
				effectComboBox.setValue(bv.effect);
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
			} finally {
				updateUI();
			}
		}
		
		private List<Build> lastTopBuilds = new ArrayList<Build>();
		
		private void updateUI() {
			buildsVisitedField.setText(model.getBuildsVisited().toString());
			
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
			valueTab = makeValueTab();
			runTab = makeRunTab();
			
			tabPane = new TabPane();
			tabPane.getTabs().add(loadTab);
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
		Text setup1 = new Text("Before beginning, extract the Grim Dawn source files.");
		Text setup2 = new Text("Complete the Modding Beginner's Guide I, section 1.2.3 Setting Up Asset Manager:");
		TextField setup3 = new TextField("http://www.grimdawn.com/forums/showpost.php?p=483411&postcount=2");
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
	
	public Tab makeValueTab() {
		Tab valueTab = new Tab();
		valueTab.setText("Star Value");
		valueTab.closableProperty().set(false);
		
		effectWeights = FXCollections.observableArrayList();
		
		ListView<EffectWeight> effectWeightsView = new ListView<EffectWeight>();
		effectWeightsView.setItems(effectWeights);
		effectWeightsView.setCellFactory(new Callback<ListView<EffectWeight>, ListCell<EffectWeight>>() {
			@Override public ListCell<EffectWeight> call(ListView<EffectWeight> list) {
				return new EffectWeightCell();
			}
		});
		
		BorderPane buttonBar = new BorderPane();
		
		Button valueButton = new Button();
		valueButton.setText("New Value");
		valueButton.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent ae) {
				effectWeights.add(new EffectWeight());
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
		for (EffectWeight ew : effectWeights) {
			if (ew.effect != null) starValueMap.put(ew.effect, ew.weight);
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
