var url = '/library/xml/?request=0&'; // this bit will have to be put into the page being generated dynamically if used again
var xmlhttp;

function getProgress(){
	xmlhttp = new XMLHttpRequest();
	xmlhttp.onreadystatechange=xmlhttpstatechanged;
	xmlhttp.open('GET', url, true);
	xmlhttp.send(null);
}

function xmlhttpstatechanged(){
	if(xmlhttp.readyState==4){
		var resp = xmlhttp.responseXML;
		var progresscontainer = document.getElementById('librarian-search-status');
		progresscontainer.replaceChild(resp.getElementsByTagName('progress')[0].cloneNode(true), progresscontainer.getElementsByTagName('table')[0]);
		if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('RequestState').value=='FINISHED')
			document.getElementById('results').appendChild(resp.getElementsByTagName('result')[0].cloneNode(true));
		else if(resp.getElementsByTagName('progress')[0].attributes.getNamedItem('RequestState').value=='ERROR')
			document.getElementById('errors').appendChild(resp.getElementsByTagName('error')[0].cloneNode(true));
		else
			var t = setTimeout('getProgress()', 1000);
	}
}
getProgress();

function toggleResult(key){
	var togglebox = document.getElementById('result-hiddenblock-'+key);
	if(togglebox.style.display == 'block')
		togglebox.style.display = 'none';
	else
		togglebox.style.display = 'block';
}
