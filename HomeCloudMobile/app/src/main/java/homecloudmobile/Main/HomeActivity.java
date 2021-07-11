package homecloudmobile.Main;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import homecloudmobile.NetworkDiscovery.HomeCloudListener;
import homecloudmobile.NetworkDiscovery.HomeCloudNetworking;

public class HomeActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE  = 100;
    private Object statusObject = new Object();
    private String addressDevice1   = null;
    private String addressDevice2   = null;
    private HomeCloudNetworking homeCloud = null;
    private HomeCloudListener listener = new HomeCloudListener() {
        // udp szerver
        @Override public void serverStarted() {
            UILogger("Server started");
        }
        @Override public void serverStopped() {
            UILogger("Server stopped");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Button startStopServer = (Button) findViewById(R.id.startStopServer);
                    startStopServer.setText("Start Server");

                    Switch onOffSwitch = (Switch) findViewById(R.id.appMode);
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
        @Override public void connectionEstablished( String address) {
            UILogger("Connection established");
            addressDevice2 = address;

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        View watchDirectory = findViewById(R.id.watchDirectory);
                        watchDirectory.setEnabled(true);
                    } else {
                        UILogger("Watch directory: API level too low (<26)");
                    }
                    View fileTransfer = findViewById(R.id.fileTransfer);
                    fileTransfer.setEnabled(true);
                }
            }, 100);
        }
        // Udp kliens
        @Override public void discoveryStarted() {
            UILogger("Discovery started");
        }
        @Override public void discoveryStopped() {
            UILogger("Discovery stopped");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Button startStopDiscovery = (Button) findViewById(R.id.startStopDiscovery);
                    startStopDiscovery.setText("Start Discovery");

                    Switch onOffSwitch = (Switch) findViewById(R.id.appMode);
                    String state = (String) onOffSwitch.getText();
                    if( state.equals("Client Mode") ) {
                        startStopDiscovery.setEnabled(true);
                    }
                }
            }, 100);
        }
        @Override public void discoveredServer(String address) {
            UILogger("Server discovered");
            addressDevice1 = address;
            if( homeCloud.isDiscoveryRunning() ){
                homeCloud.stopDiscovery();
            }
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        View watchDirectory = findViewById(R.id.watchDirectory);
                        watchDirectory.setEnabled(true);
                    } else {
                        UILogger("Watch directory: api too low (<26)");
                    }
                    View fileTransfer = findViewById(R.id.fileTransfer);
                    fileTransfer.setEnabled(true);
                }
            }, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        //  -- INICIALIZALAS --
        View default_startStopDiscovery = findViewById(R.id.startStopDiscovery);
        default_startStopDiscovery.setEnabled(false);
        View default_watchDirectory = findViewById(R.id.watchDirectory);
        default_watchDirectory.setEnabled(false);
        View default_fileTransfer = findViewById(R.id.fileTransfer);
        default_fileTransfer.setEnabled(false);

        // -- changeAppMode --
        Switch onOffSwitch = (Switch) findViewById(R.id.appMode);
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) { // Server Mode -> Client Mode
                    buttonView.setText("Client Mode");
                    View startStopDiscovery = findViewById(R.id.startStopDiscovery);
                    startStopDiscovery.setEnabled(true);
                    View startStopServer = findViewById(R.id.startStopServer);
                    startStopServer.setEnabled(false);
                    UILogger("Changed to \"Client Mode\"");
                } else {    // Client Mode -> Server Mode
                    buttonView.setText("Server Mode");
                    View startStopDiscovery = findViewById(R.id.startStopDiscovery);
                    startStopDiscovery.setEnabled(false);
                    View startStopServer = findViewById(R.id.startStopServer);
                    startStopServer.setEnabled(true);
                    UILogger("Changed to \"Server Mode\"");
                }
            }
        });

        checkPermission( Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
    }

    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(HomeActivity.this,permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(HomeActivity.this, new String[] { permission },requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(HomeActivity.this, "Storage Permission Granted",Toast.LENGTH_SHORT).show();
            } else {
                finish();
                System.exit(0);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_items, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch( item.getItemId() ){
            case R.id.help:
                Intent intent = new Intent(this, Help.class);
                startActivity(intent);
                return true;
            case R.id.exit:
                try {
                    if(homeCloud != null) {
                        if (homeCloud.isServerRunning()) {
                            homeCloud.stopServer();
                        }
                        if (homeCloud.isDiscoveryRunning()) {
                            homeCloud.stopDiscovery();
                        }
                    }
                } catch(Exception e) { Log.e("output", "Error during exit."); }
                finish();
                System.exit(0);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void handleStartStopServer(View view){
        try {
            Button startStopServer = (Button) view;
            String mode = (String) startStopServer.getText();

            if( mode.equals("Start Server") ){
                if (homeCloud == null){
                    homeCloud = new HomeCloudNetworking(  "HomeCloud", "224.0.0.1", 4446, listener);
                }
                homeCloud.startServer();
                startStopServer.setText("Stop Server");

            } else if( mode.equals("Stop Server") ){
                homeCloud.stopServer();
                startStopServer.setEnabled(false);
            }
        } catch(Exception e){
            Log.e("output" ,"HomeActivity> handleStartStopServer");
        }
    }

    public void handleStartStopDiscovery(View view){
        try {
            Button startStopDiscovery = (Button) view;
            String mode = (String) startStopDiscovery.getText();

            if( mode.equals("Start Discovery") ){
                if (homeCloud == null){
                    homeCloud = new HomeCloudNetworking(  "HomeCloud", "224.0.0.1", 4446, listener);
                }
                homeCloud.startDiscovery();
                startStopDiscovery.setText("Stop Discovery");

            } else if( mode.equals("Stop Discovery") ){
                homeCloud.stopDiscovery();
                startStopDiscovery.setEnabled(false);
            }
        } catch(Exception e){
            Log.e("output" ,"HomeActivity> handleStartStopDiscovery");
        }
    }

    public void handleWatchDirectory(View view){
        Intent intent = new Intent(this, WatchDirectoryUI.class);
        intent.putExtra("address", checkAddresses() );
        startActivity(intent);
    }

    public void handleFileTransfer(View view){
        Intent intent = new Intent(this, FileTransferUI.class);
        intent.putExtra("address", checkAddresses() );
        startActivity(intent);
    }

    private String checkAddresses(){
        if( addressDevice1 == null )        return addressDevice2;
        else if( addressDevice2 == null )   return addressDevice1;
        else                                return addressDevice1;
    }

    private void UILogger(String msg){
        synchronized(statusObject){
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TextView statusMsg = (TextView) findViewById(R.id.statusMsg);
                    statusMsg.setText(msg);
                    Log.d("output" ,"UILogger> " + msg);
                }
            }, 100);
        }
    }

}