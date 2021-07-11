package WatchDirectory;

/* A  Watch a directory-UI valtoztatasa szerver es kliens oldalrol */
public interface  WatchDirectoryUIsetter {

    void FailedFilesChecked();

    void serverStarted();
    void stopWatchHandlerUI();
    void setListViewToWatchHandler( String elem );

    void clientStarted();
    void stopWatchListenerUI();
    void setListViewToWatchListener( String elem );

}