package uk.ac.cam.cl.juliet.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.microsoft.graph.core.exceptions.ClientException;
import com.microsoft.graph.concurrency.ICallback;
import com.microsoft.graph.beta.models.DriveItem;
import com.microsoft.identity.client.MsalClientException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import uk.ac.cam.cl.juliet.R;
import uk.ac.cam.cl.juliet.activities.MainActivity;
import uk.ac.cam.cl.juliet.adapters.FilesListAdapter;
import uk.ac.cam.cl.juliet.computationengine.Burst;
import uk.ac.cam.cl.juliet.connection.ConnectionSimulator;
import uk.ac.cam.cl.juliet.data.AuthenticationManager;
import uk.ac.cam.cl.juliet.data.DriveAnalysisCallback;
import uk.ac.cam.cl.juliet.data.GraphServiceController;
import uk.ac.cam.cl.juliet.data.InternalDataHandler;
import uk.ac.cam.cl.juliet.models.SingleOrManyBursts;

/** Fragment for the 'data' screen. */
public class DataFragment extends Fragment
        implements FilesListAdapter.OnDataFileSelectedListener,
                MainActivity.PermissionListener,
                View.OnClickListener {

    public static final String FOLDER_PATH = "folder_path";

    private RecyclerView filesRecyclerView;
    private TextView noFilesToDisplayText;
    private FilesListAdapter adapter;
    private ProgressBar loadingFilesSpinner;

    private File currentDirectory;
    private SingleOrManyBursts currentNode;
    private List<SingleOrManyBursts> filesList;
    private Button plotAllFilesButton;
    private boolean checkingSync;

    private DataFragmentListener listener;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);

        // Get the context
        Context context = getContext();
        if (context == null) return null;

        // Extract the folder path for this DataFragment to display
        Bundle arguments = getArguments();
        if (arguments == null
                || !arguments.containsKey(FOLDER_PATH)
                || !(arguments.get(FOLDER_PATH) instanceof String)) {
            return null; // TODO: handle this better
        }
        String folderPath = arguments.getString(FOLDER_PATH);
        currentDirectory = new File(folderPath);
        filesList = new ArrayList<>();
        currentNode = new SingleOrManyBursts(filesList, currentDirectory, false);

        // Set up the files list UI
        filesRecyclerView = view.findViewById(R.id.filesListRecyclerView);
        filesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FilesListAdapter(filesList);
        adapter.setOnDataFileSelectedListener(this);
        filesRecyclerView.setAdapter(adapter);

        // Find the "no files to display" message
        noFilesToDisplayText = view.findViewById(R.id.noFilesText);
        setNoFilesMessageVisibility(false);

        // Find the files loading spinner
        loadingFilesSpinner = view.findViewById(R.id.loadingFilesProgressSpinner);
        loadingFilesSpinner.setVisibility(View.INVISIBLE);

        // Set up and potentially disable the "plot all files" button
        plotAllFilesButton = view.findViewById(R.id.displayAllFilesButton);
        if (!getEligibleForPlottingAllFiles()) {
            plotAllFilesButton.setEnabled(false);
        }
        plotAllFilesButton.setOnClickListener(this);

        // Subscribe for permission updates
        MainActivity main = (MainActivity) getActivity();
        if (main != null) main.addListener(this);

        checkingSync = false;

        // Return the View that was created
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFiles();
        if (listener != null) {
            listener.notifyIsActiveFragment(this);
        }
    }

    /**
     * Updates the visibility of the "no files to display" message.
     *
     * <p>If there are no files loaded then the message will be displayed; otherwise it will be
     * hidden.
     */
    private void setNoFilesMessageVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        noFilesToDisplayText.setVisibility(visibility);
    }

    /**
     * Displays a dialog when a file is selected.
     *
     * @param file The file from the list that the user selected.
     */
    @Override
    public void onDataFileClicked(
            final SingleOrManyBursts file, final FilesListAdapter.FilesListViewHolder viewHolder) {
        Context context = getContext();
        if (context == null) return;
        if (file.getIsSingleBurst()) {

            // Set the selected data to the correct file
            InternalDataHandler idh = InternalDataHandler.getInstance();
            idh.setSingleSelected(file);

            // Show the plot of the data that the user just selected
            Activity activity = getActivity();
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).showChartScreen(true);
            }

        } else {
            displayNestedFolder(file);
        }
    }

    /**
     * Handles displaying the UI for an inner folder in place of this fragment.
     *
     * @param folder The folder to display
     */
    private void displayNestedFolder(SingleOrManyBursts folder) {
        if (listener != null) {
            listener.onInnerFolderClicked(folder);
        }
    }

    @Override
    public boolean onDataFileLongClicked(
            final SingleOrManyBursts file, final FilesListAdapter.FilesListViewHolder viewHolder) {
        Context context = getContext();
        if (context == null) return false;
        int titleRes = file.getFile().isFile() ? R.string.file_selected : R.string.folder_selected;
        int messageRes =
                file.getFile().isFile() ? R.string.what_do_with_file : R.string.what_do_with_folder;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleRes);
        builder.setMessage(messageRes);
        boolean signedIn;
        try {
            signedIn = AuthenticationManager.getInstance().isUserLoggedIn();
        } catch (MsalClientException e) {
            e.printStackTrace();
            signedIn = false;
        }
        DialogInterface.OnClickListener deleteListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        showConfirmDeleteDialog(file);
                    }
                };
        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                };
        if (signedIn) {
            builder.setPositiveButton(
                    R.string.sync,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            uploadFile(file, viewHolder);
                        }
                    });
            builder.setNeutralButton(R.string.delete, deleteListener);
            builder.setNegativeButton(R.string.cancel, cancelListener);
        } else {
            builder.setPositiveButton(R.string.delete, deleteListener);
            builder.setNegativeButton(R.string.cancel, cancelListener);
        }
        builder.create().show();
        return true;
    }

    /**
     * Triggers all the files in a collection to be plotted, and subsequently the UI to swap from
     * displaying the list of files to the plot of the collection.
     */
    private void plotCollection() {
        Context context = getContext();
        if (context == null) return;
        if (ConnectionSimulator.getInstance().getDataReady()) {
            // Currently receiving live data: not safe to plot this collection so show a dialog
            new AlertDialog.Builder(context)
                    .setTitle(R.string.data_collection_in_progress)
                    .setMessage(R.string.cant_plot_collection_while_live_message)
                    .setPositiveButton(
                            R.string.dismiss,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                    .show();
        } else {
            // Not receiving live data so safe to plot this collection
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showChartScreen(false);
            }
            InternalDataHandler idh = InternalDataHandler.getInstance();
            idh.setCollectionSelected(currentNode);
        }
    }

    /**
     * Determines whether the folder we are current looking at is appropriate for plotting all
     * files.
     *
     * <p>This will be true if the current folder is both non-empty and contains only files (no
     * folders). Otherwise, this will be false.
     *
     * @return true if eligible; false otherwise
     */
    private boolean getEligibleForPlottingAllFiles() {
        InternalDataHandler idh = InternalDataHandler.getInstance();
        if (idh.getProcessingData()) return false;
        if (filesList.isEmpty()) return false;
        if (currentNode == null) return false;
        boolean eligible = true;
        for (SingleOrManyBursts file : filesList) {
            eligible &= file.getIsSingleBurst();
        }
        return eligible;
    }

    /**
     * Tests whether it is appropriate to show the "upload all files" button, and updates the UI
     * accordingly.
     */
    private void updatePlotAllFilesButtonEnabled() {
        plotAllFilesButton.setEnabled(getEligibleForPlottingAllFiles());
    }

    /** Shows a dialog message to confirm whether a file or folder should be deleted. */
    private void showConfirmDeleteDialog(final SingleOrManyBursts file) {
        Context context = getContext();
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_delete);
        int messageResource =
                file.getFile().isFile()
                        ? R.string.are_you_sure_delete_file
                        : R.string.are_you_sure_delete_folder;
        builder.setMessage(messageResource);
        builder.setPositiveButton(
                R.string.delete,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteFileOrFolder(file.getFile());
                        refreshFiles();
                        dialog.cancel();
                    }
                });
        builder.setNegativeButton(
                R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        builder.create().show();
    }

    /**
     * Deletes the passed file.
     *
     * <p>If the passed file is a folder, then this will recursively delete everything inside the
     * folder before deleting the folder itself.
     *
     * @param fileOrFolder The file (or folder) to delete
     */
    private void deleteFileOrFolder(File fileOrFolder) {
        if (fileOrFolder.isFile()) {
            fileOrFolder.delete();
        } else {
            for (File f : fileOrFolder.listFiles()) {
                deleteFileOrFolder(f);
            }
            fileOrFolder.delete();
        }
    }

    /**
     * Reloads and redraws the list of files.
     *
     * <p>Call when the set of files has been modified.
     */
    public void notifyFilesChanged() {
        adapter.notifyDataSetChanged();
    }

    /**
     * Handles spawning a background thread and uploading a file to OneDrive.
     *
     * <p>As well as uploading the file, this method also handles showing and subsequently hiding
     * the progress spinner for the relevant RecyclerView row.
     *
     * @param file The file to upload
     * @param viewHolder The ViewHolder of the row that was selected
     */
    private void uploadFile(
            SingleOrManyBursts file, FilesListAdapter.FilesListViewHolder viewHolder) {
        if (listener == null) return;
        listener.uploadFile(this, viewHolder, file, currentNode);
    }

    /** Called on permission granted - refresh file listing */
    @Override
    public void onPermissionGranted() {
        refreshFiles();
    }

    /**
     * Sets the listener for when a folder is clicked.
     *
     * @param listener The listener that will handle displaying the inner folder in place of this
     *     fragment
     */
    public void setDataFragmentListener(DataFragmentListener listener) {
        this.listener = listener;
    }

    /** Uploads the unsynced files in the directory */
    public void uploadUnsyncedFiles() throws IOException {
        final InternalDataHandler idh = InternalDataHandler.getInstance();
        final GraphServiceController gsc = new GraphServiceController();
        uploadFiles();
    }

    /** A method for beginning to upload all of the files to One Drive */
    private void uploadFiles() {
        final InternalDataHandler idh = InternalDataHandler.getInstance();
        final GraphServiceController gsc = new GraphServiceController();
        // TODO: Maybe batch these for performance issues
        for (final SingleOrManyBursts singleOrMany : filesList) {
            if (singleOrMany.getIsSingleBurst()) {
                try {
                    gsc.uploadDatafile(
                            idh.getRelativeFromAbsolute(singleOrMany.getFile().getAbsolutePath()),
                            idh.getRelativeFromAbsolute(currentDirectory.getAbsolutePath()),
                            idh.convertToBytes(singleOrMany.getFile()),
                            new ICallback<DriveItem>() {
                                @Override
                                public void success(DriveItem driveItem) {
                                    singleOrMany.setSyncStatus(true);
                                    adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void failure(ClientException ex) {
                                    Context context = getContext();
                                    if (context != null) {
                                        Toast.makeText(
                                                        context,
                                                        "Failed to upload: "
                                                                + singleOrMany.getNameToDisplay(),
                                                        Toast.LENGTH_LONG)
                                                .show();
                                    }

                                    ex.printStackTrace();
                                }
                            });
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.displayAllFilesButton:
                plotCollection();
        }
    }

    /**
     * Notifies this fragment that the user's sign in status has changed.
     *
     * <p>If true is passed then the upload status indicator will be shown for every row; otherwise
     * it will be hidden.
     */
    public void notifySignInStatusChanged(boolean signedIn) {
        int numberOfViews = filesRecyclerView.getChildCount();
        for (int i = 0; i < numberOfViews; i++) {
            FilesListAdapter.FilesListViewHolder viewHolder =
                    (FilesListAdapter.FilesListViewHolder)
                            filesRecyclerView.getChildViewHolder(filesRecyclerView.getChildAt(i));
            viewHolder.setSyncStatusVisibility(signedIn);
        }
    }

    /** Asynchronously reloads and synchronously redraws the list of files. */
    public void refreshFiles() {
        if (!isNetworkConnected()) {
            try {
                AuthenticationManager auth = AuthenticationManager.getInstance();
                if (auth.isUserLoggedIn()) {
                    AuthenticationManager.getInstance().disconnect();
                    listener.notifyNoInternet();
                    Toast.makeText(getContext(), "No Internet Connection", Toast.LENGTH_SHORT)
                            .show();
                }
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "No Internet Connection", Toast.LENGTH_SHORT).show();
                }
            } catch (MsalClientException ex) {
                ex.printStackTrace();
            }
        }

        if (!checkingSync) {
            checkingSync = true;
            new RefreshFilesTask(currentDirectory, this)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void setCheckingSync(boolean value) {
        this.checkingSync = value;
    }

    /** Check to see if the current directory's files are synced */
    private void areFilesSynced() throws FileNotFoundException {
        final InternalDataHandler idh = InternalDataHandler.getInstance();
        GraphServiceController gsc = new GraphServiceController();
        // Iterate over the files and if they're in the current directory then set sync status
        for (SingleOrManyBursts single : filesList) {
            if (single.getIsSingleBurst()) {
                if (idh.getSyncedFiles()
                        .contains(
                                idh.getRelativeFromAbsolute(single.getFile().getAbsolutePath()))) {
                    single.setSyncStatus(true);
                }
            }
        }
        gsc.getFolder(
                idh.getRelativeFromAbsolute(currentDirectory.getAbsolutePath()),
                new DriveAnalysisCallback(currentDirectory, this, filesList, adapter));
    }

    /** Handles reloading and redrawing the list of files. */
    private static class RefreshFilesTask extends AsyncTask<Void, Void, Void> {

        private File folder;
        private DataFragment dataFragment;

        public RefreshFilesTask(File directory, DataFragment dataFragment) {
            folder = directory;
            this.dataFragment = dataFragment;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dataFragment.loadingFilesSpinner != null) {
                dataFragment.loadingFilesSpinner.setVisibility(View.VISIBLE);
            }
            if (dataFragment.filesRecyclerView != null) {
                dataFragment.filesRecyclerView.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            dataFragment.filesList.clear();
            if (!folder.isFile()) {
                for (File file : folder.listFiles()) {
                    SingleOrManyBursts inner;
                    if (file.isFile()) {
                        inner = new SingleOrManyBursts((Burst) null, file, false);
                    } else {
                        inner =
                                new SingleOrManyBursts(
                                        (ArrayList<SingleOrManyBursts>) null, file, false);
                    }
                    dataFragment.filesList.add(inner);
                }
            } // TODO: Throw exception if attempt to load files from a file??
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            dataFragment.updatePlotAllFilesButtonEnabled();
            dataFragment.adapter.notifyDataSetChanged();
            dataFragment.setNoFilesMessageVisibility(dataFragment.filesList.isEmpty());
            dataFragment.loadingFilesSpinner.setVisibility(View.INVISIBLE);
            dataFragment.filesRecyclerView.setVisibility(View.VISIBLE);
            try {
                AuthenticationManager auth = AuthenticationManager.getInstance();
                // If the user is logged in and the authentication result isn't null
                if (auth.isUserLoggedIn() && auth.getAuthResult() != null) {
                    // Check to see if the files of the currentDirectory are synced
                    dataFragment.areFilesSynced();
                }
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (MsalClientException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isNetworkConnected() {
        Activity activity = getActivity();
        if (activity != null) {
            ConnectivityManager conManager =
                    (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = conManager.getActiveNetworkInfo();
            if (netInfo == null) {
                return false;
            } else {
                return netInfo.isConnectedOrConnecting();
            }
        }
        return false;
    }

    /**
     * Used by a wrapper class so that this instance can be replaced with another instance to
     * display the contents of the folder that was selected.
     */
    public interface DataFragmentListener {

        /**
         * Instructs the container to show the chosen folder.
         *
         * @param innerFolder The folder to display
         */
        void onInnerFolderClicked(SingleOrManyBursts innerFolder);

        /**
         * Instructs the container to upload the chosen file.
         *
         * @param parent The DataFragment containing this fragment // TODO: Why is this here?
         * @param viewHolder The ViewHolder for the row that was selected
         * @param file The file that is to be uploaded
         */
        void uploadFile(
                DataFragment parent,
                FilesListAdapter.FilesListViewHolder viewHolder,
                SingleOrManyBursts file,
                SingleOrManyBursts folder);

        /** Notifies the container that this fragment is now the active one */
        void notifyIsActiveFragment(DataFragment activeFragment);

        /** Notify on network changed */
        void notifyNoInternet();
    }
}
