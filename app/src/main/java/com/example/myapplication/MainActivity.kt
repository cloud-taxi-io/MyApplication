package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    // Initialized lazily because getString() requires Activity context
    private val account: Auth0 by lazy {
        Auth0.getInstance(
            getString(R.string.com_auth0_client_id),
            getString(R.string.com_auth0_domain)
        )
    }
    private val manager: SecureCredentialsManager by lazy {
        SecureCredentialsManager(
            AuthenticationAPIClient(account),
            this,
            SharedPreferencesStorage(this)
        )
    }

    // Compose re-renders the UI when these change
    private var credentials by mutableStateOf<Credentials?>(null)
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Restore session from disk; refreshes the access token if expired
        manager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) {
                credentials = result
                isLoading = false
            }

            override fun onFailure(error: CredentialsManagerException) {
                Log.e(TAG, "Failed to restore credentials", error)
                isLoading = false
            }
        })

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isLoading) {
                            Text("Loading...")
                        } else if (credentials == null) {
                            Button(onClick = { login("signup") }) { Text("Sign Up") }
                            Button(onClick = { login() }) { Text("Log In") }
                        } else {
                            val user = credentials?.user
                            Text("Logged in as ${user?.email}")
                            Text("sub: ${user?.getId()}")
                            Text("name: ${user?.name}")
                            Text("email_verified: ${user?.isEmailVerified}")
                            Button(onClick = { logout() }) { Text("Log Out") }
                        }
                    }
                }
            }
        }
    }

    private fun login(screenHint: String? = null) {
        WebAuthProvider.login(account)
            .withScheme(getString(R.string.com_auth0_scheme))
            // offline_access: requests a refresh token for session persistence
            .withScope("openid profile email offline_access")
            .withParameters(buildMap { screenHint?.let { put("screen_hint", it) } })
            .start(this, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    credentials = result
                    manager.saveCredentials(result)
                }

                override fun onFailure(error: AuthenticationException) {
                    Log.e(TAG, "Login failed", error)
                    Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun logout() {
        WebAuthProvider.logout(account)
            .withScheme(getString(R.string.com_auth0_scheme))
            .start(this, object : Callback<Void?, AuthenticationException> {
                override fun onSuccess(result: Void?) {
                    credentials = null
                    manager.clearCredentials()
                }

                override fun onFailure(error: AuthenticationException) {
                    Log.e(TAG, "Logout failed", error)
                    Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}
