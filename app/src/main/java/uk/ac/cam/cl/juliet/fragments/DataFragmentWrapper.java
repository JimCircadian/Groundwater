package uk.ac.cam.cl.juliet.fragments;

import static uk.ac.cam.cl.juliet.fragments.DataFragment.FOLDER_PATH;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.IOException;
import uk.ac.cam.cl.juliet.R;
import uk.ac.cam.cl.juliet.adapters.FilesListAdapter;
import uk.ac.cam.cl.juliet.data.AuthenticationManager;
import uk.ac.cam.cl.juliet.data.InternalDataHandler;
import uk.ac.cam.cl.juliet.models.SingleOrManyBursts;

/**
 * Contains the active <code>DataFragment</code> and handles all fragment transactions required for
 * navigating the file structure tree.
 */
public class DataFragmentWrapper extends Fragment
        implements DataFragment.DataFragmentListener {

    private MenuItem signIn;
    private MenuItem signOut;
    private MenuItem uploadAllFilesButton;
    private DataFragment currentFragment;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data_wrapper, container, false);
        setHasOptionsMenu(true);
        Bundle arguments = new Bundle();
        arguments.putString(DataFragment.FOLDER_PATH, getRootPath());
        currentFragment = new DataFragment();
        currentFragment.setArguments(arguments);
        currentFragment.setDataFragmentListener(this);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager
                    .beginTransaction()
                    .add(R.id.dataFragmentContent, currentFragment)
                    .commit();
        }
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.menu_sync, menu);
        // Init the menu items
        signIn = menu.getItem(0);
        signOut = menu.getItem(1);
        uploadAllFilesButton = menu.findItem(R.id.sync_all_files_button);

        // Try silently logging in
        AuthenticationManager authManager = AuthenticationManager.getInstance();
        displayCorrectAuthButtons();
        updateUploadAllFilesButtonVisibility();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_all_files_button:
                showSyncDialog();
                return true;
            case R.id.refresh:
                // TODO: Update files in the DataFragment
                Log.d("DataFragmentWrapper", "Update your files!");
                currentFragment.refreshFiles();
                return true;
            case R.id.sign_in_button:
                // Handling Microsoft connection
                connect();
                return true;
            case R.id.sign_out_button:
                signOut.setVisible(false);
                signIn.setVisible(true);
                updateUploadAllFilesButtonVisibility();
                if (currentFragment != null) {
                    currentFragment.notifySignInStatusChanged(false);
                }
        }
        return false;
    }

    /**
     * Shows or hides the "upload all files" button based on whether the user is logged in.
     *
     * <p>If the user is signed in then the "upload all files" button will be shown; otherwise it
     * will be hidden.
     */
    private void updateUploadAllFilesButtonVisibility() {
        if (uploadAllFilesButton == null) return;
        boolean loggedIn = false;
        uploadAllFilesButton.setEnabled(loggedIn);
        uploadAllFilesButton.setVisible(loggedIn);
    }

    private String getRootPath() {
        InternalDataHandler idh = InternalDataHandler.getInstance();
        String path = idh.getRoot().getAbsolutePath();
        return path;
    }

    /**
     * A method for checking the current authentication status and setting the correct sign in or
     * out buttons
     */
    private void displayCorrectAuthButtons() {
        if (getView() == null || signIn == null || signOut == null) return;
        signIn.setVisible(false);
        signOut.setVisible(true);
    }

    /**
     * A method that is called on tab selection - checking for a user still logged in
     *
     * @param isVisibleToUser
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        // Handle viewing the correct menu buttons
        displayCorrectAuthButtons();
    }

    @Override
    public void onInnerFolderClicked(SingleOrManyBursts innerFolder) {
        if (innerFolder.getIsSingleBurst()) return; // Should not happen...

        currentFragment = new DataFragment();
        Bundle arguments = new Bundle();
        arguments.putString(FOLDER_PATH, innerFolder.getFile().getAbsolutePath());
        currentFragment.setArguments(arguments);
        currentFragment.setDataFragmentListener(this);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.dataFragmentContent, currentFragment, currentFragment.getTag())
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void uploadFile(
            DataFragment parent,
            FilesListAdapter.FilesListViewHolder viewHolder,
            SingleOrManyBursts file,
            SingleOrManyBursts folder) {

    }

    /** Displays a dialog for syncing the files with the server. */
    private void showSyncDialog() {
        Context context = getContext();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(R.string.upload_unsynchronised_files)
                .setMessage(R.string.upload_unsyncronised_files_content)
                .setPositiveButton(
                        R.string.upload_all,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                uploadAllUnsyncedFiles();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(
                        R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                .show();
    }

    /** Uploads all unsynced files to OneDrive. */
    private void uploadAllUnsyncedFiles() {
        try {
            currentFragment.uploadUnsyncedFiles();
        } catch (IOException io) {
            Context context = getContext();
            if (context != null) {
                Toast.makeText(
                                context,
                                "There was something wrong with the file data!",
                                Toast.LENGTH_LONG)
                        .show();
            }
            io.printStackTrace();
        }
    }

    @Override
    public void notifyIsActiveFragment(DataFragment activeFragment) {
        currentFragment = activeFragment;
    }

    @Override
    public void notifyNoInternet() {
        displayCorrectAuthButtons();
        updateUploadAllFilesButtonVisibility();
    }

    /** Begins the authentication process with Microsoft */
    private void connect() {
        // Get the Authentication Manager Instance
        AuthenticationManager authManager = AuthenticationManager.getInstance();


    }
}
