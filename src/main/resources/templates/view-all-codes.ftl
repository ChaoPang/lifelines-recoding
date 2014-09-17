<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<div class="row-fluid">
	<div class="span12">
	<center><h3>View all codes</h3></center>
	</div>
</div><br>
<div class="row-fluid">
	<div class="offset3 span3">
		<strong>Select a code system : </strong>
	</div>
	<div id="select-codesystem" class="span3">
		
	</div>
</div><br>
<div class="row-fluid">
	<div id="container" class="offset3 span6">
		
	</div>
</div>
<script>
	$(document).ready(function(){
		molgenis.findAllCodeSystems(function(data){
			var select = $('<select />').css('float','right').appendTo('#select-codesystem');
			$.each(data.results, function(index, hit){
				select.append('<option value="' + hit.columnValueMap.name + '">' + hit.columnValueMap.name + '</option>');
			});
			select.change(function(){
				retrieveAllCodes(select.val(), $('#container'));
			});
			retrieveAllCodes(select.val(), $('#container'));
		});
		
		function retrieveAllCodes(codeSystem, container){
			$.ajax({
				type : 'GET',
				url :  '/view/alldocs?codeSystem=' + codeSystem,
				contentType : 'application/json',
				success : function(data){
					var table = $('<table />').addClass('table table-bordered');
					table.append('<tr><th>Name</th><th>Code</th><th>Code system</th><th>Original</th><th>Frequency</th></tr>');
					var results = data.results.sort(function(a,b){
						return molgenis.naturalSort(b.columnValueMap.code, a.columnValueMap.code);
					});
					$.each(results, function(index, hit){
						var columnValueMap = hit.columnValueMap;
						table.append('<tr><td>' + columnValueMap.name + '</td><td>' + columnValueMap.code + '</td><td>' + columnValueMap.codesystem + '</td><td>' + columnValueMap.original + '</td><td>' + hit.frequency + '</td></tr>');
					});
					$(container).empty().append(table);
				}
			});
		}
	});
</script>
<@footer/>