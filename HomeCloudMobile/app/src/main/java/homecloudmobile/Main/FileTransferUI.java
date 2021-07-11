package homecloudmobile.Main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import homecloudmobile.FileTransfer.Client;
import homecloudmobile.FileTransfer.FileTransferUISetter;
import homecloudmobile.FileTransfer.Server;

public class FileTransferUI extends AppCompatActivity {

    private String address;
    private String filePath;
    private final Object statusObject = new Object();
    private Server server = null;
    private Client client = null;
    private FileTransferUISetter setter = new FileTransferUISetter() {

        @Override public void serverStarted() {
            UILogger("Server started");
        }
        @Override public void serverStopped() {
            UILogger("Server stopped");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Button startStopServer = findViewById(R.id.startStopServerFileTransfer);
                    startStopServer.setText("Start Server");

                    Switch onOffSwitch = findViewById(R.id.appModeFileTransfer);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Server Mode") ) {
                        startStopServer.setEnabled(true);
                    }
                }
            }, 100);
        }
        @Override public void clientConnected() {
            UILogger("Client connected");
        }
        @Override public void clientDisconnected() {
            UILogger("Client disconnected");
        }
        @Override public void setListViewToServer( String elem ) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Switch onOffSwitch = findViewById(R.id.appModeFileTransfer);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Server Mode") ) {
                        LinearLayout listFileTransfer = findViewById(R.id.listFileTransfer);
                        TextView textView = new TextView( getApplicationContext() );
                        textView.setText(elem);
                        listFileTransfer.addView(textView);
                    }
                }
            }, 100);
        }

        @Override public void clientStarted() {
            UILogger("Client started");
        }
        @Override public void clientStopped() {
            UILogger("Client stopped");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Button startStopClient = findViewById(R.id.startStopClientFileTransfer);
                    startStopClient.setText("Start Client");

                    Switch onOffSwitch = findViewById(R.id.appModeFileTransfer);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Client Mode") ) {
                        startStopClient.setEnabled(true);
                    }
                }
            }, 100);
        }
        @Override public void connectedToServer() {
            UILogger("Connected to server");
        }
        @Override public void setListViewToClient( ArrayList<String> elements ) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Switch onOffSwitch = findViewById(R.id.appModeFileTransfer);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Client Mode") ) {
                        LinearLayout listFileTransfer = findViewById(R.id.listFileTransfer);
                        for(String elem : elements) {
                            TextView textView = new TextView( getApplicationContext() );
                            textView.setText(elem);
                            listFileTransfer.addView(textView);
                        }
                    }
                }
            }, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filetransfer);
        Intent intent = getIntent();
        address = intent.getStringExtra("address");

        View default_startStopClientFileTransfer = findViewById(R.id.startStopClientFileTransfer);
        default_startStopClientFileTransfer.setEnabled(false);

        // -- changeAppMode --
        Switch onOffSwitch = findViewById(R.id.appModeFileTransfer);
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) { // Server Mode -> Client Mode
                    buttonView.setText("Client Mode");
                    View startStopClientFileTransfer = findViewById(R.id.startStopClientFileTransfer);
                    startStopClientFileTransfer.setEnabled(true);
                    View startStopServer = findViewById(R.id.startStopServerFileTransfer);
                    startStopServer.setEnabled(false);
                    TextView apptext = findViewById(R.id.textByAppModeFileTransfer);
                    apptext.setText("ORIGIN path:");
                    TextView apptext2 = findViewById(R.id.text2ByAppModeFileTransfer);
                    apptext2.setText("Founded files:");
                    LinearLayout listFileTransfer = findViewById(R.id.listFileTransfer);
                    listFileTransfer.removeAllViews();
                    UILogger("Changed to \"Client Mode\"");
                } else {    // Client Mode -> Server Mode
                    buttonView.setText("Server Mode");
                    View startStopClientFileTransfer = findViewById(R.id.startStopClientFileTransfer);
                    startStopClientFileTransfer.setEnabled(false);
                    View startStopServer = findViewById(R.id.startStopServerFileTransfer);
                    startStopServer.setEnabled(true);
                    TextView apptext = findViewById(R.id.textByAppModeFileTransfer);
                    apptext.setText("TARGET path:");
                    TextView apptext2 = findViewById(R.id.text2ByAppModeFileTransfer);
                    apptext2.setText("Transfered files:");
                    LinearLayout listFileTransfer = findViewById(R.id.listFileTransfer);
                    listFileTransfer.removeAllViews();
                    UILogger("Changed to \"Server Mode\"");
                }
            }
        });
    }

    private void UILogger(String msg){
        synchronized(statusObject){
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TextView statusMsg = findViewById(R.id.statusMsgFileTransfer);
                    statusMsg.setText(msg);
                    Log.d("output" ,"UILogger> " + msg);
                }
            }, 100);
        }
    }

    public void handleStartStopServer(View view){
        try {
            Button startStopServer = (Button) view;
            String mode = (String) startStopServer.getText();

            if( mode.equals("Start Server") ){
                if( filePath != null ) {
                    if (server == null) {
                        server = new Server( setter, filePath );
                    }
                    if ( !filePath.equals( server.getTargetDirectory()) ) {
                        server.setTargetDirectory( filePath );
                    }
                    server.startServer();
                    startStopServer.setText("Stop Server");
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( mode.equals("Stop Server") ){
                server.stopServer();
                startStopServer.setEnabled(false);
            }
        } catch(Exception e){
            Log.e("output" ,"FileTransferUI> handleStartStopServer\n" + e.toString());
        }
    }

    public void handleStartStopClient(View view){
        try {
            Button startStopClient = (Button) view;
            String mode = (String) startStopClient.getText();
            if( mode.equals("Start Client") ){
                if( filePath != null ) {
                    client = new Client( setter, filePath , address );
                    client.startClient();
                    startStopClient.setText("Stop Client");
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( mode.equals("Stop Client") ){
                client.stopClient();
                startStopClient.setEnabled(false);
            }
        } catch(Exception e) {
            Log.e("output" ,"FileTransferUI> handleStartStopClient\n" + e.toString());
        }
    }

    public void chooseDirectory(View view) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(Intent.createChooser(i, "Choose directory"), 9999);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == 9999){
            Uri uri = data.getData();
            //Log.e("output", "PROBA: " +  FileUtil.getFullPathFromTreeUri(uri,this) );
            filePath = findFullPath( uri.getPath() );

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TextView filePathText = findViewById(R.id.filePathFileTransfer);
                    filePathText.setText(filePath);
                }
            }, 100);
            UILogger("Directory was chosen successfully.");
        }
    }

    public static String findFullPath(String path) {
        String actualResult="";
        path=path.substring(5);
        int index=0;
        StringBuilder result = new StringBuilder("/storage");
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) != ':') {
                result.append(path.charAt(i));
            } else {
                index = ++i;
                result.append('/');
                break;
            }
        }
        for (int i = index; i < path.length(); i++) {
            result.append(path.charAt(i));
        }
        if (result.substring(9, 16).equalsIgnoreCase("primary")) {
            actualResult = result.substring(0, 8) + "/emulated/0/" + result.substring(17);
        } else {
            actualResult = result.toString();
        }
        return actualResult;
    }

}
