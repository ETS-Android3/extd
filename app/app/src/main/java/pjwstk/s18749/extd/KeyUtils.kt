package pjwstk.s18749.extd

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import java.io.IOException
import java.io.OutputStream
import java.lang.RuntimeException

public class KeyUtils {
    companion object {
        fun saveKeys(kp: KeyPair, privOut: OutputStream, pubOut: OutputStream) {
            try {
                kp.writePrivateKey(privOut)
                kp.writePublicKey(pubOut, "")

                privOut.flush()
                pubOut.flush()

            } catch (e: IOException) {
                throw RuntimeException("could not save keys")
            }
        }

        fun generateKeyPair(): KeyPair {
            return try {
                val kp = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
                kp
            } catch (e: JSchException) {
                throw RuntimeException("key generation failed")
            }
        }
    }
}
