package app.luma.torch

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FlashTile : TileService() {
    private val l = { sync() }
    override fun onStartListening() {
        Torch.init(this); Torch.listeners += l; sync()
    }
    override fun onStopListening() { Torch.listeners -= l }
    override fun onClick() { Torch.set(!Torch.on) }
    private fun sync() { qsTile?.apply { state = if (Torch.on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() } }
}
