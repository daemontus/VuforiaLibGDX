## Deprecated

Currently, the repo is deprecated, as it is not working with latest Vuforia SDK and I don't have the time to fix it (I am not doing AR any more). The repo is updated with latest SDK (as of July 2018), but the app sometimes crashes (race condition depending on whether Vuforia or LibGDX initialises first - seems to require some architectural changes compared to previous versions) and the model is not rendered (but the transform matrix seems to be computed correctly).

I'll try to fix it if I find some time in the future, but for now, consider it dead. I'm happy to give maintainer rights to anyone interested in keeping this alive.

If you wish to see the version working with older Vuforia SDK, see [here](https://github.com/daemontus/VuforiaLibGDX/tree/2fecef3c2d4699f8dcc9c2813a232f369e640013).

# VuforiaLibGDX
Example of Vuforia and LibGDX integration for 3D model rendering in augmented reality. 

For a more detailed explenation, see this [article](https://treeset.wordpress.com/2016/06/12/vuforia-and-libgdx-3d-model-renderer/).

Note: The app will freeze for a few seconds after start up while loading the 3D model, do not panic :)

##### If you are interested in older versions of Vuforia/LibGDX, check out [this branch](https://github.com/daemontus/VuforiaLibGDX/tree/old).

![Example screenshot](https://treeset.files.wordpress.com/2016/06/screenshot_2016-06-12-21-13-23.png)
