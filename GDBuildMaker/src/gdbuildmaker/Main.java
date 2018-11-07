package gdbuildmaker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
	Tab valueTab;
	Tab runTab;
	Thread uiUpdaterThread;
	
	ObservableList<String> effectsList;
	ObservableList<EffectWeight> effectWeights;
	
	TextField buildsVisitedField;
	TextArea topBuildsField;
	
	private class EffectWeight {
		public String effect = "none";
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
			effectComboBox = new ComboBox<String>(effectsList);
			effectComboBox.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent ae) {
					EffectWeightCell.this.getItem().effect =
							effectComboBox.getValue();
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
	
	private class UIUpdaterTask extends Task<Object> {
		@Override
		protected Object call() throws Exception {
			
			try {
				while (true) {
					if (isCancelled()) break;
					updateUI();
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {}
			updateUI();
			System.out.println("updater done");
			return null;
		}
		
		private void updateUI() {
			buildsVisitedField.setText(model.getBuildsVisited().toString());
			
			StringBuilder builder = new StringBuilder();
			for (Build build : model.getTopBuilds()) {
				builder.append(build.toString() + "\n");
			}
			topBuildsField.setText(builder.toString());
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	public void start(Stage primaryStage) {
		try {
			model = new Controller();
			effectsList = FXCollections.observableArrayList();
			
			valueTab = makeValueTab();
			runTab = makeRunTab();
			
			tabPane = new TabPane();
			tabPane.getTabs().add(valueTab);
			tabPane.getTabs().add(runTab);
			
			Scene scene = new Scene(tabPane, 400, 400);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.show();
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public Tab makeValueTab() {
		Tab valueTab = new Tab();
		valueTab.setText("Star Value");
		valueTab.closableProperty().set(false);
		
		Text workingDirLabel = new Text();
		workingDirLabel.setText("Grim Dawn Working Directory");
		
		TextField workingDirField = new TextField();
		workingDirField.setText("C:\\Games\\steamapps\\common\\Grim Dawn\\working");
		workingDirField.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent ae) {
				model.loadConstellations(workingDirField.getText());
				effectsList.setAll(model.getEffectList());
			}
		});
		
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
		valuePane.getChildren().add(workingDirLabel);
		valuePane.getChildren().add(workingDirField);
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
		topBuildsField.setText("one\ntwo\nthree\nfour");
		
		VBox runPane = new VBox(8);
		runPane.getChildren().add(buildsBar);
		runPane.getChildren().add(topBuildsField);
		
		runTab.setContent(runPane);
		return runTab;
	}
	
	private void handleRunButton() {
		Map<String, Double> starValueMap = new HashMap<String, Double>();
		for (EffectWeight ew : effectWeights) {
			starValueMap.put(ew.effect, ew.weight);
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
