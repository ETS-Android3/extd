package pjwstk.s18749.extd

import android.content.Intent
import android.opengl.Visibility
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_new_connection.*
import java.lang.Exception
import java.security.KeyPairGenerator
import java.security.KeyStore

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.vpPager)

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter

        val intent = intent
        val data = intent.data.toString()
        val dataArgs = data.replace("extd://", "")

        if (dataArgs != null) {
            val args = dataArgs.split(":")

            if (args.size == 3) {
                val port = Integer.parseInt(args[2])
                val ip = args[1]
                val secret = args[0]

                txIp.setText(ip)
                txPort.setText(port.toString())
                txSecret.setText(secret)

                viewPager.currentItem = 1
            }
        }

        val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        if (!ks.containsAlias("extd")) {
            try {
                Log.w("extd", "Not an instance of a PrivateKeyEntry")
                vpPager.visibility = View.GONE
                genKeys.visibility = View.VISIBLE

                val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    "AndroidKeyStore"
                )
                val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
                    "extd",
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).run {
                    setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    build()
                }

                kpg.initialize(parameterSpec)
                kpg.generateKeyPair()

                vpPager.visibility = View.VISIBLE
                genKeys.visibility = View.GONE

                Toast.makeText(
                    this,
                    "Key generation successful",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Key generation failed",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("extd", "KEy Generation Exception: $e")
                txKeyGenFailed.visibility = View.VISIBLE
            }
        }
    }


    override fun onBackPressed() {
        if (viewPager.currentItem == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed()
        } else {
            // Otherwise, select the previous step.
            viewPager.currentItem = viewPager.currentItem - 1
        }
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            if (position == 0) {
                return PageFragmentSavedConnections()
            }

            return PageFragmentNewConnection()
        }
    }
}