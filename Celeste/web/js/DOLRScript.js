// Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
//
// This code is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2
// only, as published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// General Public License version 2 for more details (a copy is
// included in the LICENSE file that accompanied this code).
//
// You should have received a copy of the GNU General Public License
// version 2 along with this work; if not, write to the Free Software
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
// 02110-1301 USA
//
// Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
// Park, CA 94025 or visit www.sun.com if you need additional
// information or have any questions.
function setInnerHTML(id, txt)
{
	document.getElementById(id).innerHTML = txt;
}

// this function is needed to work around a bug in IE related to element attributes
function hasClass(obj) {
	var result = false;
	if (obj.getAttributeNode("class") != null) {
		result = obj.getAttributeNode("class").value;
	}
	return result;
}   

// Tip-o-the-hat to David F. Miller and his A List Apart article http://www.alistapart.com/articles/zebratables
function tableStripe(id) {
	var even = true;
  
    // if arguments are provided to specify the colours of the even & odd rows, then use the them;
    // otherwise use the following defaults:
    var evenColor = arguments[1] ? arguments[1] : "#fff";
    var oddColor = arguments[2] ? arguments[2] : "#eee";
  
	// obtain a reference to the desired table, if no such table exists, abort
	var table = document.getElementById(id);
	if (!table) {
		return;
    	}
    
	// by definition, tables can have more than one tbody element, so we'll have to get the list of child tbodys 
	var tbodies = table.getElementsByTagName("tbody");

    // and iterate through them...
    for (var h = 0; h < tbodies.length; h++) {
    
     // find all the tr elements... 
      var trs = tbodies[h].getElementsByTagName("tr");
      
      // ... and iterate through them
      for (var i = 0; i < trs.length; i++) {
        //if (!hasClass(trs[i]) && !trs[i].style.backgroundColor) { // avoid rows that have a class attribute or backgroundColor style
        if (!trs[i].style.backgroundColor) { // avoid rows that have an explicit backgroundColor style
                  
          // get all the data cells in this row...
          var tds = trs[i].getElementsByTagName("td");
        
          // and iterate through them...
          for (var j = 0; j < tds.length; j++) {
            var mytd = tds[j];              
            //if (!hasClass(mytd) && !mytd.style.backgroundColor) { // avoid cells that have a class attribute or backgroundColor style
            if (!mytd.style.backgroundColor) { // avoid cells that have an explicit backgroundColor style
              mytd.style.backgroundColor = even ? evenColor : oddColor;
            }
          }
        }

        even = ! even;
      }
    }
  }                                                                     
