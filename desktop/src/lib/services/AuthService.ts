import { supabase } from '../supabase';

export class AuthService {
  /**
   * Initiates Google OAuth sign-in flow.
   * Redirects to the provided auth callback URL.
   */
  static async signInWithGoogle() {
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: 'nextpage-desktop://auth-callback',
        queryParams: {
          prompt: 'select_account',
          access_type: 'offline'
        },
        scopes: 'https://www.googleapis.com/auth/drive.file'
      }
    });

    if (error) {
      console.error('Google Sign-In Error:', error.message);
      throw error;
    }

    return data;
  }

  /**
   * Sign out the current user.
   */
  static async signOut() {
    const { error } = await supabase.auth.signOut();
    if (error) {
      console.error('Sign-Out Error:', error.message);
      throw error;
    }
  }

  /**
   * Get the current session if available.
   */
  static async getSession() {
    const { data, error } = await supabase.auth.getSession();
    if (error) {
      console.error('Get Session Error:', error.message);
      throw error;
    }
    return data.session;
  }
}
