package gdbuildmaker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class AutocompleteComboBox extends ComboBox<String> {
	
	private ObservableList<String> allItems;
	
	public AutocompleteComboBox(ObservableList<String> items) {
		super(FXCollections.observableArrayList(items));
		setEditable(true);
		
		allItems = items;
		
		getEditor().addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent keyEvent) {
				if (keyEvent.getCode().isLetterKey()
						|| keyEvent.getCode() == KeyCode.DOWN) {
					if (!isShowing()) show();
				}
			}
		});
		
		getEditor().addEventHandler(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent keyEvent) {
				if (keyEvent.getCode().isLetterKey()
						|| keyEvent.getCode() == KeyCode.BACK_SPACE
						|| keyEvent.getCode() == KeyCode.DELETE) {
					getItems().clear();
					getItems().addAll(filter(getEditor().getText()));
					
					int newRowCount = getItems().size();
					if (newRowCount > 10) newRowCount = 10;
					if (newRowCount != getVisibleRowCount()) {
						hide();
						setVisibleRowCount(newRowCount);
						show();
					}
				}
			}
		});
	}
	
	private ObservableList<String> filter(String text) {
		String ltext = text.toLowerCase();
		ObservableList<String> filteredList = FXCollections.observableArrayList();
		
		// Primary matches start with the input string
		for (String s : allItems) {
			if (s.toLowerCase().startsWith(ltext)) {
				filteredList.add(s);
			}
		}
		
		// Secondary matches contain the input string
		for (String s : allItems) {
			if (s.toLowerCase().contains(ltext) && !filteredList.contains(s)) {
				filteredList.add(s);
			}
		}
		
		// Tertiary matches contain the input string's characters in order,
		//  but maybe not sequentially
		Pattern p = Pattern.compile(String.join(".*", ltext.split("")));
		Matcher m;
		for (String s : allItems) {
			m = p.matcher(s.toLowerCase());
			if (m.find() && !filteredList.contains(s)) {
				filteredList.add(s);
			}
		}
		
		return filteredList;
	}
}
