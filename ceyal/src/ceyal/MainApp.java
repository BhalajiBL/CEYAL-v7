package ceyal;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {
    private TableView<EventLog> tableView;
    private ObservableList<EventLog> logData;
    private Pane petriNetPane;
    private ScrollPane scrollPane;
    private Slider zoomSlider;
    private double scaleFactor = 1.0;
    private List<Circle> places;
    private List<Rectangle> transitions;
    private List<Circle> tokens;
    private Label totalTimeLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Process Mining Software");

        logData = FXCollections.observableArrayList();
        tableView = new TableView<>(logData);
        initializeTableColumns();

        TextField filterField = new TextField();
        filterField.setPromptText("Filter by Event, Resource, Cost, or Duration...");
        setupFilterField(filterField);

        Button loadButton = new Button("Load Event Log");
        loadButton.setOnAction(e -> loadEventLog(primaryStage));

        Button visualizeButton = new Button("Visualize Process");
        visualizeButton.setOnAction(e -> visualizeProcess());

        Button simulateButton = new Button("Run Simulation");
        simulateButton.setOnAction(e -> simulateProcess());

        zoomSlider = createZoomSlider();
        scrollPane = new ScrollPane();
        petriNetPane = new Pane();
        setupScrollPane();

        VBox leftPanel = createLeftPanel(loadButton, visualizeButton, simulateButton, zoomSlider, filterField);
        SplitPane splitPane = createSplitPane();
        totalTimeLabel = new Label("Total Simulation Time: 0.0");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(splitPane);
        mainLayout.setBottom(new VBox(totalTimeLabel));

        Scene scene = new Scene(mainLayout, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeTableColumns() {
        TableColumn<EventLog, String> eventColumn = new TableColumn<>("Event");
        eventColumn.setCellValueFactory(cellData -> cellData.getValue().eventProperty());

        TableColumn<EventLog, String> timestampColumn = new TableColumn<>("Timestamp");
        timestampColumn.setCellValueFactory(cellData -> cellData.getValue().timestampProperty());

        TableColumn<EventLog, String> resourceColumn = new TableColumn<>("Resource");
        resourceColumn.setCellValueFactory(cellData -> cellData.getValue().resourceProperty());

        TableColumn<EventLog, String> costColumn = new TableColumn<>("Cost");
        costColumn.setCellValueFactory(cellData -> cellData.getValue().costProperty());

        TableColumn<EventLog, String> durationColumn = new TableColumn<>("Duration");
        durationColumn.setCellValueFactory(cellData -> cellData.getValue().durationProperty());

        tableView.getColumns().addAll(eventColumn, timestampColumn, resourceColumn, costColumn, durationColumn);
    }

    private void setupFilterField(TextField filterField) {
        filterField.textProperty().addListener((observable, oldValue, newValue) -> {
            tableView.setItems(logData.filtered(log ->
                    log.eventProperty().get().toLowerCase().contains(newValue.toLowerCase()) ||
                    log.resourceProperty().get().toLowerCase().contains(newValue.toLowerCase()) ||
                    log.costProperty().get().toLowerCase().contains(newValue.toLowerCase()) ||
                    String.valueOf(log.durationProperty().get()).contains(newValue)));
        });
    }

    private Slider createZoomSlider() {
        Slider slider = new Slider(0.5, 2.0, 1.0);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(0.5);
        slider.setMinorTickCount(5);
        slider.setSnapToTicks(true);
        
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            scaleFactor = newVal.doubleValue();
            petriNetPane.setScaleX(scaleFactor);
            petriNetPane.setScaleY(scaleFactor);
        });
        
        return slider;
    }

    private void setupScrollPane() {
        petriNetPane.setMinWidth(500);
        petriNetPane.setMinHeight(800);
        
        scrollPane.setContent(petriNetPane);

        petriNetPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY() > 0 ? 1.1 : 0.9;
                scaleFactor *= delta;
                petriNetPane.setScaleX(scaleFactor);
                petriNetPane.setScaleY(scaleFactor);
            }
            event.consume();
        });
    }

    private VBox createLeftPanel(Button loadButton, Button visualizeButton, Button simulateButton, Slider zoomSlider, TextField filterField) {
        VBox leftPanel = new VBox(loadButton, visualizeButton, simulateButton, zoomSlider, filterField);
        
        leftPanel.setSpacing(10);
        leftPanel.setPadding(new Insets(10));
        
        return leftPanel;
    }

    private SplitPane createSplitPane() {
        SplitPane splitPane = new SplitPane();
        
        splitPane.getItems().addAll(tableView, scrollPane);
        
        splitPane.setDividerPositions(0.4);
        
        return splitPane;
    }

    private void loadEventLog(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        
        fileChooser.setTitle("Open Event Log File");
        
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null) {
            readEventLog(file);
       }
   }

    private void readEventLog(File file) {
        logData.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // Skip the header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 5) {
                    logData.add(new EventLog(parts[0], parts[1], parts[2], parts[3], parts[4]));
                } else {
                    throw new IOException("Invalid format");
                }
            }
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error reading the file: " + e.getMessage());
            alert.showAndWait();
        }
    }

   private void visualizeProcess() {
       petriNetPane.getChildren().clear();
       places = new ArrayList<>();
       transitions = new ArrayList<>();
       tokens = new ArrayList<>();
       drawPetriNetFromLog();
   }

   private void drawPetriNetFromLog() {
       double startX = 300;
       double startY = 100;
       double verticalGap = 100;

       for (int i = 0; i < logData.size(); i++) {
           EventLog event = logData.get(i);
           Circle placeBefore = createPlace(startX, startY + (i * verticalGap));
           places.add(placeBefore);
           Rectangle transition = createTransition(startX, startY + (i * verticalGap) + verticalGap / 2);
           transitions.add(transition);
           Circle placeAfter = createPlace(startX, startY + (i * verticalGap) + verticalGap);
           places.add(placeAfter);

           drawArc(placeBefore, transition); 
           drawArc(transition, placeAfter);   
       }
   }

   private void drawArc(Circle from, Rectangle to) {
       Line arc = new Line(from.getCenterX(), from.getCenterY(), to.getX() + to.getWidth() / 2, to.getY());
       petriNetPane.getChildren().add(arc);
   }

   // Overloaded method to handle drawing from Rectangle to Circle
   private void drawArc(Rectangle from, Circle to) {
       Line arc = new Line(from.getX() + from.getWidth() / 2, from.getY(), to.getCenterX(), to.getCenterY());
       petriNetPane.getChildren().add(arc);
   }

   private Circle createPlace(double x, double y) {
       Circle place = new Circle(x, y, 15, Color.BLUE);
       petriNetPane.getChildren().add(place);
       return place;
   }

   private Rectangle createTransition(double x, double y) {
       Rectangle transition = new Rectangle(x - 15, y - 10, 30, 20);
       transition.setFill(Color.GREEN);
       petriNetPane.getChildren().add(transition);
       return transition;
   }

   private void simulateProcess() {
       totalTimeLabel.setText("Total Simulation Time: " + runSimulation() + " seconds");
   }

   private double runSimulation() {
	    double totalDuration = 0.0;

	    for (int i = 0; i < logData.size(); i++) {
	        EventLog log = logData.get(i);
	        double duration = Double.parseDouble(log.durationProperty().get());
	        totalDuration += duration;

	        Circle token = new Circle(5, Color.RED);
	        tokens.add(token);
	        petriNetPane.getChildren().add(token);

	        // Simulate token movement
	        TranslateTransition transition = new TranslateTransition(Duration.seconds(duration), token);
	        transition.setFromX(places.get(i * 2).getCenterX());
	        transition.setFromY(places.get(i * 2).getCenterY());
	        transition.setToX(places.get(i * 2 + 1).getCenterX());
	        transition.setToY(places.get(i * 2 + 1).getCenterY());

	        // Delay animation to simulate sequential token movement
	        transition.setDelay(Duration.seconds(i * 0.5));
	        transition.play();
	    }
	    return totalDuration;
	}


   public static class EventLog {
       private final SimpleStringProperty event;
       private final SimpleStringProperty timestamp;
       private final SimpleStringProperty resource;
       private final SimpleStringProperty cost;
       private final SimpleStringProperty duration;

       public EventLog(String event, String timestamp, String resource, String cost, String duration) {
           this.event = new SimpleStringProperty(event);
           this.timestamp = new SimpleStringProperty(timestamp);
           this.resource = new SimpleStringProperty(resource);
           this.cost = new SimpleStringProperty(cost);
           this.duration = new SimpleStringProperty(duration);
       }

       public SimpleStringProperty eventProperty() { return event; }
       public SimpleStringProperty timestampProperty() { return timestamp; }
       public SimpleStringProperty resourceProperty() { return resource; }
       public SimpleStringProperty costProperty() { return cost; }
       public SimpleStringProperty durationProperty() { return duration; }
   }
}