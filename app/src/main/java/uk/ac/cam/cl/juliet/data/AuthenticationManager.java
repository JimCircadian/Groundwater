package uk.ac.cam.cl.juliet.data;

import android.app.Application;

public class AuthenticationManager extends Application {
    public static final String[] SCOPES = {"openid", "Files.ReadWrite", "User.ReadBasic.All"};
    private final String TAG = "AuthenticationManager";
    private static AuthenticationManager INSTANCE;

    private AuthenticationManager() {}

    public static AuthenticationManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AuthenticationManager();
        }
        return INSTANCE;
    }

    /**
     * Check to see if a user is logged in
     *
     * @return <code>boolean</code>
     */
    public boolean isUserLoggedIn() {
        return true;
    }

    public static void reset() {
        INSTANCE = null;
    }

    public void disconnect() {}

}
