package client;

//imports
 import javafx.application.Application;
 import javafx.application.Platform;
 import javafx.concurrent.Service;
 import javafx.concurrent.Task;
 import javafx.fxml.FXMLLoader;
 import javafx.scene.Parent;
 import javafx.scene.Scene;
 import javafx.scene.control.TextArea;
 import javafx.scene.control.TextField;
 import javafx.stage.Stage;
 import logging.SetupLogger;
 import java.io.*;
 import java.net.*;
 import javax.sound.sampled.*;
 import javax.swing.*;
 import javax.sound.sampled.AudioSystem;
 import java.text.DateFormat;
 import java.text.SimpleDateFormat;
 import java.util.Properties;
 import java.util.logging.Handler;
 import java.util.logging.Level;
 import java.util.logging.Logger;


public class Client extends Application{

    private static Client client;
    public static Client getClient() {
            return client;
        }

//main
    public static void main(String[] args){
        client = new Client();
        launch(args);
    }//endMain


    /**
     * client class private variables
     */
    private BufferedReader in;
    private PrintWriter out;
    private OutputStream audioOut;
    private BufferedInputStream audioIn;
    private Stage window;
    private static final Logger LOGGER = SetupLogger.startLogger(Client.class.getName());
    private String userName = "";



    @Override
    /**
     * initializes fx gui by loading from fxml file
     */
    public void start(Stage primaryStage) throws Exception {
        this.window = primaryStage;
        Parent root = FXMLLoader.load(getClass().getResource("display/ServerScene.fxml"));                          //loads the server scene from fxml file
        primaryStage.setTitle("Chat Client");
        primaryStage.setScene(new Scene(root, 800, 400));
        primaryStage.setOnCloseRequest(e -> closeClient());
        primaryStage.show();
    }



    /**
     * closes logger files when the user closes the fx window
     */
    private void closeClient(){
        Handler[] handlers = LOGGER.getHandlers();
        LOGGER.log(Level.INFO, "User Exit", handlers);
        for(int i = 0; i < handlers.length; i++){
            handlers[i].close();
        }
        this.window.close();
    }




    /**
     * is used to re-prompt the user if the name is already taken on server
     * @return new name to attempt connection with
     */
        private String getName() {
            return JOptionPane.showInputDialog(null, "Username: \""  + userName +  "\" is taken.\nEnter a new Username", "Screen name selection", JOptionPane.PLAIN_MESSAGE);
        }




    /**
     * used to connect the client to a server, and have validated name
     * @param serverAddress the address of the server to attempt connection
     * @return successful connection to server
     */
        public boolean connect(String serverAddress){
            Socket typeS = null;//creates socket connection to server...for text
            try {
                typeS = new Socket(serverAddress, 9001);
                Socket audioS = new Socket(serverAddress, 9002);//creates another socket connection to server..for audio
                this.in = new BufferedReader(new InputStreamReader(typeS.getInputStream()));       //buffered reader to recieve from serverfor text
                this.out = new PrintWriter(typeS.getOutputStream(), true);                       //printwriter to write to server
                this.audioIn = new BufferedInputStream(audioS.getInputStream());                //input stream to recieve from server for audio
                this.audioOut = audioS.getOutputStream();

                in.readLine();
                out.println(userName);

                Task myTask = new Task() {  //this task ensure valid name
                    @Override
                    protected Object call() throws Exception {
                        while(in.readLine().startsWith("SUBMITNAME")){
                            userName = getName();
                            out.println(userName);
                        }
                        Properties userPrefObj = LoadSaveUtil.getPropertyObject(LoadSaveUtil.userSettingFilename);
                        userPrefObj.setProperty("USERNAME",userName);
                        LoadSaveUtil.savePropertyObject(userPrefObj,LoadSaveUtil.userSettingFilename);
                        return null;
                    }
                };
                myTask.run();

            }
            catch (Exception e) {
                LOGGER.log(Level.INFO, "Failed to connect to server: " + serverAddress, serverAddress);
                //e.printStackTrace();
                return false;
            }
            LOGGER.log(Level.INFO, "Connected to server: " + serverAddress, serverAddress);
            return true;
        }




    /**
     * declares and runs the service that transfers text communication
     * @param messgaeArea a reference to the gui text area so text can be appended
     */
        public void runChatService(TextArea messgaeArea){                                     //this method creates and starts the service
            Service chatService = new Service() {
                @Override
                protected Task createTask() {
                    return new Task() {
                        @Override
                        protected Object call() throws Exception {

                            boolean cont = true;
                            int namesFailed = 0;
                            while (cont) {

                                try{
                                    //for typing
                                    String line = in.readLine();

                                    if (line != null && line.startsWith("MESSAGE")) {
                                        Platform.runLater(new Runnable() {
                                            @Override
                                            public void run() {
                                                messgaeArea.appendText(line.substring(8) + "\n");
                                            }
                                        });
                                        AudioEffects.play("boop.wav");
                                    }
                                }//end try
                                catch(SocketException e){
                                    //will prevent endless loop if server goes downs
                                    LOGGER.log(Level.SEVERE, e.getMessage(), e);
                                    cont = false;
                                }
                                catch (Exception e){
                                    LOGGER.log(Level.SEVERE, e.getMessage(), e);
                                }

                            }//end while
                            return null;
                        }//end call
                    };//end new task
                }//end create task
            };//end service

            chatService.start();
        }


    /**
     * creates and starts the service that sends and recieves audio
     */
    public void runAudioService(){
        Service voiceService = new Service() {
            @Override
            protected Task createTask() {
                return new Task() {
                    @Override
                    protected Object call() throws Exception {
                        boolean cont = true;
                        while (cont) {
                            //for sounds
                            try {
                                AudioInputStream ais = AudioSystem.getAudioInputStream(audioIn);   //waits here for audio input coming from server
                                Clip clip = AudioSystem.getClip();
                                clip.open(ais);
                                clip.start();
                                clip.drain();
                            } catch(SocketException e){
                                //will prevent endless loop if server goes down
                                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                                cont = false;
                            }
                            catch (Exception e){
                                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                            }
                        }//end while
                       return null;
                    }//end call
                };//end new task
            }//end create task
        };//end new service


        voiceService.start();
    }


    /**
     * sends a string message to the server
     */
    public void sendToServer(String strToSend){
        out.println(strToSend);
    }

    /**
     * sets the username
     * @param newUsername new username
     */
    public void setName(String newUsername){
        userName = newUsername;
    }



//    //maybe move to LoadSave class*******************************
    private void sendLine(){

        File audioFile = new File("RecentAudio.wav");


        try {
            FileInputStream fin = new FileInputStream(audioFile);
            OutputStream os = audioOut;
            byte buffer[] = new byte[2048];//weird array thing*
            int count;
            while((count = fin.read(buffer)) != -1){
                os.write(buffer, 0, count);
            }
        }
        catch(Exception e){
            //TODO
        }

    }//end sendLine


    //maybe move to LoadSave class**********************************************
    private void voiceLine() {

        final Record line = new Record();
        Thread stopper = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(5000);//change time if needed
                } catch (InterruptedException ie) {
                    //TODO
                }
                line.stopRec();
            }
        });
        stopper.start();
        line.startRec();

    }


    //maybe move to LoadSave class
//    private void saveConv(){
//        String fileName = JOptionPane.showInputDialog("Enter file name");
//        if(fileName != null && !fileName.equals("")) {
//            try {
//                File file1 = new File("SavedConversations\\");                                       //made it save to central folder
//                file1.mkdirs();
//                File file2 = new File(file1, fileName + ".txt");
//                if (!file2.exists()) {
//                    file2.createNewFile();
//                }
//                BufferedWriter temp = new BufferedWriter(new FileWriter(file2, false));
//                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//                String convo = getConvo();                                                                            //get text from message area
//                temp.write("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\n" +
//                        convo + "\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<" + (dateFormat.format(new Date())));
//                temp.close();
//            } catch (Exception ex) {
//                System.out.println("Error in save conversation Event Handler");
//            }
//        }
//    }

}
