package pjwstk.s18749.extd

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import pjwstk.s18749.extd.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pagerAdapter = ScreenSlidePagerAdapter(this)
        binding.vpPager.adapter = pagerAdapter

        val intent = intent
        val data = intent.data.toString()
        val dataArgs = data.replace("extd://", "")

        if (dataArgs != null) {
            val args = dataArgs.split(":")

            if (args.size == 3) {
                val port = Integer.parseInt(args[2])
                val ip = args[1]
                val secret = args[0]

//                txIp.setText(ip)
//                txPort.setText(port.toString())
//                txSecret.setText(secret)
//
//                viewPager.currentItem = 1
            }
        }

        val priv = File(applicationContext.filesDir,"id_rsa")
        val pub = File(applicationContext.filesDir,"id_rsa.pub")

        if (!priv.exists() || !pub.exists()) {
            binding.genKeys.visibility = View.VISIBLE
            val kp = getKeyPair()

            if (kp != null) {
                binding.vpPager.visibility = View.VISIBLE
                binding.genKeys.visibility = View.GONE

                try {
                    val fosPriv = openFileOutput("id_rsa", Context.MODE_PRIVATE)
                    val fosPub = openFileOutput("id_rsa.pub", Context.MODE_PRIVATE)

                    fosPriv.write(kp.private.encoded)
                    fosPub.write(kp.public.encoded)

                    fosPriv.flush()
                    fosPub.flush()

                    fosPriv.close()
                    fosPub.close()

                    Toast.makeText(
                        this,
                        "ok: ${priv.exists()} ${pub.exists()}",
                        Toast.LENGTH_LONG
                    ).show()

//                    Toast.makeText(
//                        this,
//                        "Key generation successful",
//                        Toast.LENGTH_LONG
//                    ).show()

                } catch (e: IOException) {
                    Toast.makeText(
                        this,
                        "Could not save keys",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.d("extd", "Key saving failed: $e")
                    binding.txKeyGenFailed.visibility = View.VISIBLE
                }
            } else {
                Toast.makeText(
                    this,
                    "Key generation failed",
                    Toast.LENGTH_LONG
                ).show()

                binding.txKeyGenFailed.visibility = View.VISIBLE
            }
        }
    }

    fun getKeyPair(): KeyPair? {
        return try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(4096)
            kpg.generateKeyPair()

        } catch (e: Exception) {
            Log.d("extd", "KEy Generation Exception: $e")
            null
        }
    }


    override fun onBackPressed() {
        if (binding.vpPager.currentItem == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed()
        } else {
            // Otherwise, select the previous step.
            binding.vpPager.currentItem = binding.vpPager.currentItem - 1
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