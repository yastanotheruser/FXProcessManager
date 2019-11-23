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
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class FXProcessManager extends Application {

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
        initInactiveProcessesPane();
        initActiveProcessesPane();

        Scene scene = new Scene(root, 640, 480);
        primaryStage.setTitle("FXProcessManager");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest((WindowEvent event) -> pm.destroy());
        primaryStage.show();
        root.requestFocus();
    }

    private void initInactiveProcessesPane() {
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
        btn1.setOnAction((ActionEvent event) -> {
            int processIndex = processIndices.get(selectionModel.getSelectedIndex());
            pm.start(state.getProcess(processIndex), ProcessPriority.NORMAL, 100, 10);
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
        TableView cycleTable = new TableView();
        cycleTable.setPlaceholder(new Label("No existen procesos activos"));
        TableColumn checkboxColumn = new TableColumn();
        checkboxColumn.setGraphic(new CheckBox());
        TableColumn pidColumn = new TableColumn("PID");
        TableColumn processColumn = new TableColumn("Proceso");
        TableColumn stateColumn = new TableColumn("Estado");
        processColumn.prefWidthProperty().bind(
            cycleTable.widthProperty().subtract(
                checkboxColumn.getWidth() + pidColumn.getWidth() + stateColumn.getWidth()
            )
        );
        cycleTable.getColumns().addAll(checkboxColumn, pidColumn, processColumn, stateColumn);

        HBox controls = new HBox();
        controls.setPadding(new Insets(10, 15, 10, 15));
        controls.setSpacing(20);
        controls.setAlignment(Pos.CENTER);
        Button btn1 = new Button("Detener");
        btn1.setMinWidth(100);
        Button btn2 = new Button("Pausar");
        btn2.setMinWidth(100);
        VBox progressPane = new VBox();
        progressPane.setSpacing(10);
        HBox.setHgrow(progressPane, Priority.ALWAYS);
        Label progressLabel = new Label("Tiempo de proceso ejecutado");
        ProgressBar progressBar = new ProgressBar(0.5);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(progressBar, Priority.ALWAYS);
        progressPane.getChildren().addAll(progressLabel, progressBar);
        controls.getChildren().addAll(btn1, btn2, progressPane);

        pm.watch((ArrayList<ProcessState> changes) -> {
            ArrayList<ProcessInstance> instances = new ArrayList<>();
            ProcessInstance executingInstance = pm.getExecutingInstance();
            if (executingInstance != null) {
                instances.add(executingInstance);
            }
            instances.addAll(pm.lists.get(ProcessState.READY));
            instances.addAll(pm.lists.get(ProcessState.SUSPENDED));
            instances.addAll(pm.lists.get(ProcessState.INACTIVE));
            System.out.println(changes);
        });

        vbox.getChildren().addAll(cycleTable, controls);
        vbox.setPadding(Insets.EMPTY);
        this.children.add(titledPane);
    }

    public static void main(String[] args) {
        launch(args);
    }

}
