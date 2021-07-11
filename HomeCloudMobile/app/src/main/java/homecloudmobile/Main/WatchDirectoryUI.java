package homecloudmobile.Main;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import homecloudmobile.WatchDirectory.WatchDirectoryUIsetter;
import homecloudmobile.WatchDirectory.WatchHandler;
import homecloudmobile.WatchDirectory.WatchListener;

public class WatchDirectoryUI extends AppCompatActivity {

    private String address;
    private String filePath;
    private final Object statusObject = new Object();
    private int WatchHandlerStatus = 0;
    private Object WatchHandlerStatusLock = new Object();
    private WatchHandler server  = null;
    private WatchListener client = null;
    private WatchDirectoryUIsetter setter = new WatchDirectoryUIsetter() {

        @Override public void FailedFilesChecked() {
            UILogger("Failed files are checked");
        }
        @Override public void serverStarted() {
            UILogger("Server started");
        }

        @Override public void stopWatchHandlerUI() {
            synchronized (WatchHandlerStatusLock){
                WatchHandlerStatus++;
                if(WatchHandlerStatus == 3){
                    WatchHandlerStatus = 0;
                    UILogger("Server stopped");

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Button startStopServer = findViewById(R.id.startStopServerWatchDirectory);
                            startStopServer.setText("Start Server");

                            Switch onOffSwitch = findViewById(R.id.appModeWatchDirectory);
                            String state = (String) onOffSwitch.getText();
                            if( state.equals("Server Mode") ) {
                                startStopServer.setEnabled(true);
                            }
                        }
                    }, 100);
                }
            }
        }
        @Override public void setListViewToWatchHandler( String elem) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Switch onOffSwitch = findViewById(R.id.appModeWatchDirectory);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Server Mode") ) {
                        LinearLayout listWatchDirectory = findViewById(R.id.listWatchDirectory);
                        TextView textView = new TextView( getApplicationContext() );
                        textView.setText(elem);
                        listWatchDirectory.addView(textView);
                    }
                }
            }, 100);
        }
        @Override public void clientStarted() {
            UILogger("Client started");
        }
        @Override public void stopWatchListenerUI() {
            UILogger("Client stopped");

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Button startStopClient = findViewById(R.id.startStopClientWatchDirectory);
                    startStopClient.setText("Start Client");

                    Switch onOffSwitch = findViewById(R.id.appModeWatchDirectory);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Client Mode") ) {
                        startStopClient.setEnabled(true);
                    }
                }
            }, 100);
        }
        @Override public void setListViewToWatchListener( String elem) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Switch onOffSwitch = findViewById(R.id.appModeWatchDirectory);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Client Mode") ) {
                        LinearLayout listWatchDirectory = findViewById(R.id.listWatchDirectory);
                        TextView textView = new TextView( getApplicationContext() );
                        textView.setText(elem);
                        listWatchDirectory.addView(textView);
                    }
                }
            }, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watchdirectory);
        Intent intent = getIntent();
        address = intent.getStringExtra("address");

        View default_startStopClient = findViewById(R.id.startStopClientWatchDirectory);
        default_startStopClient.setEnabled(false);

        // -- changeAppMode --
        Switch onOffSwitch = findViewById(R.id.appModeWatchDirectory);
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) { // Server Mode -> Client Mode
                    buttonView.setText("Client Mode");
                    View startStopClient = findViewById(R.id.startStopClientWatchDirectory);
                    startStopClient.setEnabled(true);
                    View startStopServer = findViewById(R.id.startStopServerWatchDirectory);
                    startStopServer.setEnabled(false);
                    View failedFiles = findViewById(R.id.checkFailedFiles);
                    failedFiles.setEnabled(false);
                    TextView apptext = findViewById(R.id.textByAppModeWatchDirectory);
                    apptext.setText("TARGET path:");
                    TextView apptext2 = findViewById(R.id.text2ByAppModeWatchDirectory);
                    apptext2.setText("Transfered files:");
                    LinearLayout listFileTransfer = findViewById(R.id.listWatchDirectory);
                    listFileTransfer.removeAllViews();
                    UILogger("Changed to \"Client Mode\"");
                } else {    // Client Mode -> Server Mode
                    buttonView.setText("Server Mode");
                    View startStopClient = findViewById(R.id.startStopClientWatchDirectory);
                    startStopClient.setEnabled(false);
                    View startStopServer = findViewById(R.id.startStopServerWatchDirectory);
                    startStopServer.setEnabled(true);
                    View failedFiles = findViewById(R.id.checkFailedFiles);
                    failedFiles.setEnabled(true);
                    TextView apptext = findViewById(R.id.textByAppModeWatchDirectory);
                    apptext.setText("ORIGIN path:");
                    TextView apptext2 = findViewById(R.id.text2ByAppModeWatchDirectory);
                    apptext2.setText("Event changes:");
                    LinearLayout listFileTransfer = findViewById(R.id.listWatchDirectory);
                    listFileTransfer.removeAllViews();
                    UILogger("Changed to \"Server Mode\"");
                }
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void handleCheckFailedFiles(View view){
        if (server != null) {
            UILogger("Checking failed files");
            server.WatchHandlerCheckFailedFiles();
        }
    }

    private void UILogger(String msg){
        synchronized(statusObject){
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TextView statusMsg = findViewById(R.id.statusMsgWatchDirectory);
                    statusMsg.setText(msg);
                    Log.d("output" ,"UILogger> " + msg);
                }
            }, 100);
        }
    }

    public void chooseDirectory(View view) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(Intent.createChooser(i, "Choose directory"), 9998);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == 9998){
            Uri uri = data.getData();
            filePath = findFullPath( uri.getPath() );

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TextView filePathText = findViewById(R.id.filePathWatchDirectory);
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void handleStartStopServer(View view){
        try {
            Button startStopServer = (Button) view;
            String mode = (String) startStopServer.getText();

            if( mode.equals("Start Server") ){
                if( filePath != null ) {
                    if (server == null) {
                        server = new WatchHandler(setter, filePath, address);
                    }
                    if ( !filePath.equals( server.getFilePath() ) ) {
                        server.setFilePath( filePath );
                    }
                    server.startWatchService();
                    startStopServer.setText("Stop Server");
                    setter.serverStarted();
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if( mode.equals("Stop Server") ){
                server.stopWatchService();
                startStopServer.setEnabled(false);
            }
        } catch(Exception e){
            Log.e("output", "WatchDirectoryUI> handleStartStopServer\n" + e.toString());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void handleStartStopClient(View view) {
        try {
            Button startStopClient = (Button) view;
            String mode = (String) startStopClient.getText();
            if (mode.equals("Start Client")) {
                if (filePath != null) {
                    if (client == null) {
                        client = new WatchListener(setter, filePath, address);
                    }
                    if (!filePath.equals( client.getTargetPath() )) {
                        client.setTargetPath( filePath );
                    }
                    client.startWatchListener();
                    startStopClient.setText("Stop Client");
                    setter.clientStarted();
                } else {
                    UILogger("Choosing of the directory failed.");
                }
            } else if (mode.equals("Stop Client")) {
                client.stopWatchListener();
                startStopClient.setEnabled(false);
            }
        } catch (Exception e) {
            Log.e("output", "WatchDirectoryUI> handleStartStopClient\n" + e.toString());
        }

    }

}