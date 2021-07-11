package homecloudmobile.WatchDirectory;

public interface  WatchDirectoryUIsetter {

    void FailedFilesChecked();

    void serverStarted();
    void stopWatchHandlerUI();
    void setListViewToWatchHandler( String elem );

    void clientStarted();
    void stopWatchListenerUI();
    void setListViewToWatchListener( String elem );

}