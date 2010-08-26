dojo.require("dijit.Tooltip");
dojo.provide("sunlabs.StickyTooltip");
dojo.declare("sunlabs.StickyTooltip", [dijit.Tooltip], {

// Inspired by a forum post at http://dojotoolkit.org/forum/dijit-dijit-0-9/dijit-support/links-tooltips
  postCreate: function() {
    if(!dijit._masterTT) {
      dijit._masterTT = new dijit._MasterTooltip();
    }
    // should get the connection list & see if another
    // sunlabs.StickyTooltip
    // has already made these connections.
    dijit._masterTT.connect(dijit._masterTT.domNode,'onmouseover',this.ttShow);
    dijit._masterTT.connect(dijit._masterTT.domNode,'onmouseout',this.ttHide);
    this.inherited("postCreate", arguments);
  },

  ttShow: function (evt) {
    // console.log("persist");
    this.fadeOut.stop();
    this.fadeIn.play();
  },

  ttHide: function (evt) {
    // console.log("fade");
    this.fadeOut.play();
  }
}
);
