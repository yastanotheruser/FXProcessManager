package fxprocessmanager;

import com.sun.javafx.PlatformUtil;
import fxprocessmanager.process.Process;
import fxprocessmanager.process.ProcessInstance;
import fxprocessmanager.process.ProcessManager;
import fxprocessmanager.process.ProcessPriority;
import fxprocessmanager.process.ProcessState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class FXProcessManager extends Application {

    public static final class ProcessInstanceRow {
        private final static Map<ProcessState, String> localeStateStringMap = new HashMap<ProcessState, String>() {{
            put(ProcessState.INACTIVE, "Inactivo");
            put(ProcessState.READY, "Preparado");
            put(ProcessState.EXECUTING, "En ejecución");
            put(ProcessState.SUSPENDED, "Suspendido");
        }};

        private final static Map<ProcessPriority, String> localePriorityStringMap = new HashMap<ProcessPriority, String>() {{
            put(ProcessPriority.LOW, "Baja");
            put(ProcessPriority.NORMAL, "Normal");
            put(ProcessPriority.HIGH, "Alta");
            put(ProcessPriority.HIGHEST, "Muy alta");
        }};

        private final static Map<ProcessInstance, CheckBox> instanceCheckBoxes = new WeakHashMap<>();

        public final static String getLocaleStateString(ProcessState state) {
            return localeStateStringMap.get(state);
        }

        public final static String getLocalePriorityString(ProcessPriority priority) {
            return localePriorityStringMap.get(priority);
        }

        private final ProcessInstance instance;
        private final CheckBox checkBox;
        private final int pid;
        private final String name;
        private final String stateString;
        private final String priorityString;
        private final int processTime;
        private final String isReadingString;

        public ProcessInstanceRow(ProcessInstance instance) {
            if (!instanceCheckBoxes.containsKey(instance)) {
                instanceCheckBoxes.put(instance, new CheckBox());
            }
            this.instance = instance;
            this.checkBox = instanceCheckBoxes.get(instance);
            this.pid = instance.getPID();
            this.name = instance.getProcess().getName();
            this.stateString = getLocaleStateString(instance.info.getState());
            this.priorityString = getLocalePriorityString(instance.getPriority());
            this.processTime = instance.getProcessTime();
            if (instance.info.isReading()) {
                this.isReadingString = "Sí";
            } else {
                this.isReadingString = "No";
            }
        }

        public ProcessInstance getInstance() {
            return instance;
        }

        public CheckBox getCheckBox() {
            return checkBox;
        }

        public int getPID() {
            return pid;
        }

        public String getName() {
            return name;
        }

        public String getStateString() {
            return stateString;
        }

        public String getPriorityString() {
            return priorityString;
        }

        public long getProcessTime() {
            return processTime;
        }

        public String getIsReadingString() {
            return isReadingString;
        }
    }

    private static final class ProcessInstanceRowSelectionTableCell extends TableCell<ProcessInstanceRow, CheckBox> {
        @Override
        public void updateItem(CheckBox item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
                setGraphic(null);
                return;
            }
            setGraphic(empty ? null : item);
            this.setAlignment(Pos.CENTER);
        }
    }

    private static final Random random = new Random();
    private final FXProcessManagerState state;
    private final ProcessManager pm;
    private VBox root;
    private ObservableList<Node> children;
    private ObservableList<String> processNames;
    private ArrayList<Integer> processIndices;

    public FXProcessManager() throws Exception {
        super();
        Path dirPath;
        File file;
        if (PlatformUtil.isWindows()) {
            Path path = Paths.get(System.getenv("APPDATA"));
            dirPath = path.resolve("fxpm");
        } else if (PlatformUtil.isLinux()) {
            Path path = Paths.get(System.getenv("HOME"));
            dirPath = path.resolve(".fxpm");
        } else {
            throw new Exception("Current platform is not supported");
        }

        File dir = dirPath.toFile();
        if (!dir.exists()) {
            dir.mkdir();
        }
        file = dirPath.resolve("state").toFile();
        this.state = new FXProcessManagerState(file);
        this.pm = this.state.getProcessManager();
    }

    @Override
    public void start(Stage primaryStage) {
        root = new VBox();
        children = this.root.getChildren();
        initProcessListPane();
        initActiveProcessesPane();
        initOptionsPane();

        Scene scene = new Scene(root, 960, 720);
        primaryStage.setTitle("FXProcessManager");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest((WindowEvent event) -> pm.destroy());
        primaryStage.show();
        root.requestFocus();
    }

    private void initProcessListPane() {
        VBox vbox = new VBox();
        vbox.setPadding(new Insets(10));
        TitledPane titledPane = new TitledPane("Lista de procesos", vbox);

        Label label = new Label("Procesos");
        Process[] processes = state.getProcesses();
        processIndices = new ArrayList<>();
        for (int i = 0; i < processes.length; i++) {
            processIndices.add(i);
        }
        processNames = FXCollections.observableArrayList(
            Arrays.stream(processes).map(p -> p.getName()).collect(Collectors.toList())
        );
        ComboBox comboBox = new ComboBox(processNames);
        SingleSelectionModel selectionModel = comboBox.getSelectionModel();
        comboBox.setPromptText("Seleccione un proceso");
        comboBox.setMaxWidth(Double.MAX_VALUE);
        HBox hbox1 = new HBox(label, comboBox);
        hbox1.setPadding(new Insets(10));
        hbox1.setSpacing(10);
        hbox1.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(comboBox, Priority.ALWAYS);

        Button btn1 = new Button("Ejecutar");
        btn1.setMinWidth(100);
        btn1.disableProperty().bind(comboBox.valueProperty().isNull());
        btn1.setOnAction((ActionEvent event) -> {
            int index = selectionModel.getSelectedIndex();
            if (index == -1) {
                return;
            }

            int processIndex = processIndices.get(index);
            ProcessInstance instance = pm.start(
                state.getProcess(processIndex),
                ProcessPriority.getValue(random.nextInt(ProcessPriority.count)),
                100 + random.nextInt(201),
                10 + random.nextInt(41)
            );
            instance.info.setReadState(random.nextBoolean());
        });

        Button btn2 = new Button("Añadir proceso");
        btn2.setMinWidth(100);
        TextInputDialog dialog = new TextInputDialog();
        dialog.setContentText("Nombre del proceso");
        dialog.setHeaderText("Añadir proceso");
        dialog.setTitle("FXProcessManager");
        btn2.setOnAction((ActionEvent event) -> {
            TextField field = dialog.getEditor();
            field.setText("");
            dialog.show();
            field.requestFocus();
        });
        dialog.setOnCloseRequest((DialogEvent event) -> {
            String name = dialog.getResult();
            if (name == null || name.length() == 0) {
                return;
            }

            Process proc = new Process(name);
            state.addProcess(proc);
            processNames.add(name);
            processIndices.add(state.getProcessCount() - 1);
            try {
                state.saveState();
            } catch (IOException ex) {
                Logger.getLogger(FXProcessManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        Button btn3 = new Button("Eliminar");
        btn3.setMinWidth(100);
        btn3.disableProperty().bind(comboBox.valueProperty().isNull());
        btn3.setOnAction((ActionEvent event) -> {
            int index = selectionModel.getSelectedIndex();
            if (index == -1) {
                return;
            }

            int processIndex = processIndices.get(index);
            state.removeProcess(processIndex);
            processNames.remove(index);
            int optionCount = processNames.size();
            selectionModel.selectNext();
            try {
                state.saveState();
            } catch (IOException ex) {
                Logger.getLogger(FXProcessManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        HBox hbox2 = new HBox(btn1, btn2, btn3);
        hbox2.setSpacing(15);
        hbox2.setAlignment(Pos.CENTER);
        vbox.getChildren().addAll(hbox1, hbox2);
        this.children.add(titledPane);
    }

    private void initActiveProcessesPane() {
        VBox vbox = new VBox();
        TitledPane titledPane = new TitledPane("Procesos activos", vbox);
        TableView<ProcessInstanceRow> procTable = new TableView<>();
        procTable.setRowFactory(row -> new TableRow<ProcessInstanceRow>() {
            @Override
            public void updateItem(ProcessInstanceRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    return;
                }

                String color = null;
                ProcessInstance instance = item.getInstance();
                if (instance.info.getState() != null) {
                    switch (instance.info.getState()) {
                        case INACTIVE:
                            color = "#c7c7c7";
                            break;
                        case READY:
                            color = "#c5e1a5";
                            break;
                        case SUSPENDED:
                            color = "#bbdefb";
                            break;
                        case EXECUTING:
                            color = "#8bc34a";
                            break;
                        default:
                            break;
                    }
                }

                String style = "";
                if (color != null) {
                    style += "-fx-background-color: " + color + ";";
                }
                if (pm.isPaused(instance)) {
                    style += "-fx-opacity: 0.75;";
                }
            }
        });
        procTable.setPlaceholder(new Label("No existen procesos activos"));
        procTable.setFocusTraversable(false);
        procTable.setItems(FXCollections.observableArrayList());
        ObservableSet<ProcessInstanceRow> selectionSet = FXCollections.observableSet(new HashSet<>());
        IntegerBinding itemsLengthBinding = Bindings.size(procTable.getItems());
        IntegerBinding selectionLengthBinding = Bindings.size(selectionSet);
        BooleanBinding emptinessBinding = itemsLengthBinding.isEqualTo(0);
        BooleanBinding selectionEmptinessBinding = selectionLengthBinding.isEqualTo(0);
        BooleanBinding selectionCompletenessBinding = selectionLengthBinding.isEqualTo(itemsLengthBinding)
                .and(selectionLengthBinding.greaterThan(0));
        TableColumn checkboxColumn = new TableColumn();
        CheckBox selectAllCheckBox = new CheckBox();
        selectAllCheckBox.disableProperty().bind(emptinessBinding);
        selectAllCheckBox.setOnAction(event -> {
            boolean selected = selectAllCheckBox.isSelected();
            ObservableList<ProcessInstanceRow> items = procTable.getItems();
            for (ProcessInstanceRow pi : procTable.getItems()) {
                CheckBox cb = pi.getCheckBox();
                cb.setSelected(selected);
                if (cb.isSelected()) {
                    selectionSet.add(pi);
                } else {
                    selectionSet.remove(pi);
                }
            }
        });
        checkboxColumn.setSortable(false);
        checkboxColumn.setGraphic(selectAllCheckBox);
        checkboxColumn.setCellValueFactory(new PropertyValueFactory<>("checkBox"));
        checkboxColumn.setCellFactory(column -> {
            ProcessInstanceRowSelectionTableCell cell = new ProcessInstanceRowSelectionTableCell();
            ChangeListener<Boolean> selectionChangeListener = (ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                ProcessInstanceRow row = (ProcessInstanceRow) cell.getTableRow().getItem();
                if (newValue) {
                    selectionSet.add(row);
                } else {
                    selectionSet.remove(row);
                }
                selectAllCheckBox.setSelected(selectionCompletenessBinding.get());
            };
            cell.itemProperty().addListener((ObservableValue<? extends CheckBox> observable, CheckBox oldValue, CheckBox newValue) -> {
                if (oldValue != null) {
                    oldValue.selectedProperty().removeListener(selectionChangeListener);
                }
                if (newValue == null) {
                    return;
                }
                newValue.selectedProperty().addListener(selectionChangeListener);
            });
            return cell;
        });
        TableColumn pidColumn = new TableColumn("PID");
        pidColumn.setCellValueFactory(new PropertyValueFactory<>("PID"));
        TableColumn processColumn = new TableColumn("Proceso");
        processColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn stateColumn = new TableColumn("Estado");
        stateColumn.setCellValueFactory(new PropertyValueFactory<>("stateString"));
        TableColumn priorityColumn = new TableColumn("Prioridad");
        priorityColumn.setCellValueFactory(new PropertyValueFactory<>("priorityString"));
        TableColumn ptimeColumn = new TableColumn("Tiempo de proceso");
        ptimeColumn.setCellValueFactory(new PropertyValueFactory<>("processTime"));
        TableColumn inputColumn = new TableColumn("Interacción");
        inputColumn.setCellValueFactory(new PropertyValueFactory<>("isReadingString"));
        procTable.getColumns().addAll(
            checkboxColumn,
            pidColumn,
            processColumn,
            stateColumn,
            priorityColumn,
            ptimeColumn,
            inputColumn
        );
        int columnCount = procTable.getColumns().size();
        procTable.getColumns().forEach(c -> {
            TableColumn column = (TableColumn) c;
            column.prefWidthProperty().bind(procTable.widthProperty().divide(columnCount));
        });

        HBox controls = new HBox();
        controls.setPadding(new Insets(10, 15, 10, 15));
        controls.setSpacing(20);
        controls.setAlignment(Pos.CENTER);
        Button btn1 = new Button("Avanzar");
        btn1.setMinWidth(100);
        btn1.disableProperty().bind(Bindings.size(procTable.itemsProperty().get()).isEqualTo(0));
        btn1.setOnAction(event -> pm.nextTick());
        Button btn2 = new Button("Detener");
        btn2.setMinWidth(100);
        btn2.disableProperty().bind(selectionEmptinessBinding);
        btn2.setOnAction(event -> {
            ProcessInstanceRow[] selectionSetItems = new ProcessInstanceRow[selectionSet.size()];
            selectionSet.toArray(selectionSetItems);
            for (ProcessInstanceRow row : selectionSetItems) {
                pm.stop(row.instance);
                row.getCheckBox().setSelected(false);
            }
            selectionSet.clear();
        });
        SimpleBooleanProperty allPausableProperty = new SimpleBooleanProperty(false);
        SimpleBooleanProperty allResumableProperty = new SimpleBooleanProperty(false);
        Button btn3 = new Button("Pausar / Reanudar");
        btn3.setMinWidth(100);
        btn3.setDisable(true);
        selectionSet.addListener(new SetChangeListener<ProcessInstanceRow>() {
            private boolean hasChanged = false;

            @Override
            public void onChanged(SetChangeListener.Change<? extends ProcessInstanceRow> change) {
                if (hasChanged) {
                    return;
                }
                hasChanged = true;
                Platform.runLater(() -> {
                    updatePauseToggleButton();
                    hasChanged = false;
                });
            }

            private void updatePauseToggleButton() {
                boolean allPausable = false;
                boolean allResumable = false;
                for (ProcessInstanceRow row : selectionSet) {
                    ProcessInstance instance = row.getInstance();
                    if (!allPausable && !allResumable) {
                        if (!pm.isPaused(instance)) {
                            allPausable = true;
                        } else {
                            allResumable = true;
                        }
                    } else if (allPausable) {
                        if (pm.isPaused(instance)) {
                            allPausable = false;
                            break;
                        }
                    } else {
                        if (!pm.isPaused(instance)) {
                            allResumable = false;
                            break;
                        }
                    }
                }

                allPausableProperty.set(allPausable);
                allResumableProperty.set(allResumable);
                if (allPausable) {
                    btn3.setText("Pausar");
                    btn3.setDisable(false);
                } else if (allResumable) {
                    btn3.setText("Reanudar");
                    btn3.setDisable(false);
                } else {
                    btn3.setText("Pausar / Reanudar");
                    btn3.setDisable(true);
                }
            }
        });
        btn3.setOnAction(event -> {
            boolean allPausable = allPausableProperty.get();
            boolean allResumable = allResumableProperty.get();
            if (!allPausable && !allResumable) {
                return;
            }
            ProcessInstanceRow[] selectionSetItems = new ProcessInstanceRow[selectionSet.size()];
            selectionSet.toArray(selectionSetItems);
            for (ProcessInstanceRow row : selectionSetItems) {
                if (allPausable) {
                    pm.pause(row.instance);
                } else if (allResumable) {
                    pm.resume(row.instance);
                }
                row.getCheckBox().setSelected(false);
            }
            selectionSet.clear();
        });
        VBox progressPane = new VBox();
        progressPane.setSpacing(10);
        HBox.setHgrow(progressPane, Priority.ALWAYS);
        Label progressLabel = new Label("Tiempo de proceso ejecutado");
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        Tooltip progressTooltip = new Tooltip();
        VBox.setVgrow(progressBar, Priority.ALWAYS);
        progressPane.getChildren().addAll(progressLabel, progressBar);
        controls.getChildren().addAll(btn1, btn2, btn3, progressPane);

        pm.watch((Set<ProcessState> changes) -> {
            ObservableList<ProcessInstance> instances = FXCollections.observableArrayList();
            ProcessInstance executingInstance = pm.getExecutingInstance();
            if (executingInstance != null) {
                double progress = executingInstance.getProgress();
                int executed = executingInstance.info.getExecuted();
                int total = executingInstance.getProcessTime();
                instances.add(executingInstance);
                progressBar.setProgress(progress);
                Tooltip.install(progressBar, progressTooltip);
                progressTooltip.setText(Integer.toString(executed) + " / " + Integer.toString(total));
            } else {
                progressBar.setProgress(0);
                progressBar.setTooltip(null);
                Tooltip.uninstall(progressBar, progressTooltip);
            }
            instances.addAll(
                pm.collections.get(ProcessState.READY)
                    .stream()
                    .sorted(ProcessManager.hashComparator)
                    .collect(Collectors.toList())
            );
            instances.addAll(pm.collections.get(ProcessState.SUSPENDED));
            instances.addAll(pm.collections.get(ProcessState.INACTIVE));
            ObservableList<ProcessInstanceRow> rows = procTable.getItems();
            Collection<ProcessInstanceRow> newItems = instances.stream()
                    .sorted(Comparator.comparing(ProcessInstance::getPID))
                    .map(pi -> new ProcessInstanceRow(pi))
                    .collect(Collectors.toList());
            Collection<ProcessInstanceRow> selectedRows = newItems.stream()
                    .filter(r -> r.getCheckBox().isSelected())
                    .collect(Collectors.toList());
            rows.setAll(newItems);
            selectionSet.clear();
            selectionSet.addAll(selectedRows);
        });

        vbox.getChildren().addAll(procTable, controls);
        vbox.setPadding(Insets.EMPTY);
        this.children.add(titledPane);
    }

    public void initOptionsPane() {
        VBox vbox = new VBox();
        TitledPane titledPane = new TitledPane("Opciones", vbox);
        HBox timeContainer = new HBox();
        timeContainer.setPadding(new Insets(10));
        timeContainer.setSpacing(10);
        timeContainer.setAlignment(Pos.CENTER);
        Label timeLabel = new Label("Tiempo de avance (milisegundos)");
        TextInputDialog dialog = new TextInputDialog();
        dialog.setContentText("Nuevo tiempo de avance");
        dialog.setHeaderText("Tiempo de avance");
        dialog.setTitle("FXProcessManager");
        TextField timeField = new TextField();
        timeField.setPrefWidth(50);
        timeField.setAlignment(Pos.CENTER);
        timeField.setText("0");
        timeField.setEditable(false);
        timeField.setOnMouseClicked((MouseEvent event) -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            TextField field = dialog.getEditor();
            field.setText(timeField.getText());
            dialog.show();
            field.requestFocus();
        });
        Alert errorAlert = new Alert(AlertType.ERROR);
        Alert rangeErrorAlert = new Alert(AlertType.ERROR);
        errorAlert.setContentText("Entrada inválida");
        rangeErrorAlert.setContentText("El intervalo minimo permitido es 100ms");
        dialog.setOnCloseRequest((DialogEvent event) -> {
            String input = dialog.getResult();
            if (input == null || input.length() == 0) {
                return;
            }

            try {
                Long time = Long.valueOf(input);
                if (time != 0 && time < 100) {
                    rangeErrorAlert.show();
                    return;
                }
                pm.setTickInterval((time > 0) ? time : null);
                timeField.setText(Long.toString(time));
            } catch (NumberFormatException ex) {
                errorAlert.show();
                Logger.getLogger(FXProcessManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        timeContainer.getChildren().addAll(timeLabel, timeField);
        HBox deltaContainer = new HBox();
        deltaContainer.setPadding(new Insets(10));
        deltaContainer.setSpacing(10);
        deltaContainer.setAlignment(Pos.CENTER);
        Label deltaLabel = new Label("ΔQ");
        Slider deltaSlider = new Slider();
        deltaSlider.setMin(5);
        deltaSlider.setMax(15);
        deltaSlider.setValue(5);
        deltaSlider.setShowTickLabels(true);
        deltaSlider.setShowTickMarks(true);
        deltaSlider.setSnapToTicks(true);
        deltaSlider.setMajorTickUnit(5);
        deltaSlider.setMinorTickCount(4);
        deltaSlider.setBlockIncrement(1);
        deltaSlider.setPrefWidth(200);
        deltaSlider.valueProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            pm.setDelta(newValue.intValue());
        });
        deltaContainer.getChildren().addAll(deltaLabel, deltaSlider);
        vbox.getChildren().addAll(timeContainer, deltaContainer);
        this.children.add(titledPane);
    }

    public static void main(String[] args) {
        launch(args);
    }

}
