# Set of scripts to enable VCN screen sharing

## how it works

on the host computer using xrandr a virtual screen is created and attached to the desktop on the left, right, top or bottom.
Xorg treats this screen as a physical device.
Vnc server is started and the virtual screen from previous step is assigned as a source for vnc stream/connection.
Client devices connect to vnc server using vnc viewer and they can view or interact with the shared desktop.

## Possible problems

Not all graphic cards allow for creating VIRTUAL outputs, so sometimes using HDMI or other unused output may be needed.
xrandr setup may be complicated, for example when sharing third screen on already dual head setup.
It may be hard to provide a simple interface to setup xrandr.
Vnc connection must be encrypted and access must be controlled somehow.
In order to calculate target device setup, resolution is needed.
Maybe if there was a client server connection, app on tablet to server on desktop,
app could request connection to the desktop and all configuration would be done from client device?
Also one vnc server instance is needed for one desktop, but there can be many, so the best would be,
that the client requests access to the specific desktop at specific resolution, and all setup is performed respecting these settings.

But this requires writing and app and a server which will be dependant on the platform. And when setup is manual, you only need a vnc viewer nothing more.
So still configuration of each desktop from the host would be best. of course user should know the resolution then, and he would always need access to host to start streaming desktop.

## update

### security
ssl encryption did not want to work with realVNC android client.
Probably client server architecture is the only solution,
custom written application would have a qr code scanner to import certificate
from host, then it could use a library for encrypted openssl vnc connection if there is one
if not, there's a big problem.

of course the app would then know exact resolution etc so setup would be easier and quicker.
But the problem is, how mobile would recognize server on the computer in any network with any given ip?
I believe avahi daemon is the exact thing for this, so it would work the same as printers in local network.

so anytime you want to extend desktop, you need to be in the same network,
you open client app, it searches for the server running on your host, it connects, authenticates,
and requests screen according to how you decided to align it.

### nvidia
Nvidia sucks, and it is not possible to achieve out-of-the box setup. the only possible way to do it,
is to have a second monitor connected to the host, and then turn it off and use the setup to connect vnc.
Or you can buy a special plug, to connect pins on hdmi output, to force host nvidia driver to think
there is another monitor connected.

Nvidia sucks so much, that you cannot define any virtual or dummy output. you need a physical working output.