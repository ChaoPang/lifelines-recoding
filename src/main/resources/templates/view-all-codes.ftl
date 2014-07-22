<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<div class="row-fluid">
	<div class="span12">
	<center><h3>View all codes</h3></center>
	</div>
</div><br>
<div class="row-fluid">
	<div id="container" class="offset3 span6">
		
	</div>
</div>
<script>
	$(document).ready(function(){
		$.ajax({
			type : 'GET',
			url :  '/view/alldocs',
			contentType : 'application/json',
			success : function(data){
				var table = $('<table />').addClass('table table-bordered');
				table.append('<tr><th>Name</th><th>Code</th><th>Frequency</th></tr>');
				var results = data.results.sort(function(a,b){
					return molgenis.naturalSort(b.columnValueMap.code, a.columnValueMap.code);
				});
				$.each(results, function(index, hit){
					var columnValueMap = hit.columnValueMap;
					table.append('<tr><td>' + columnValueMap.name + '</td><td>' + columnValueMap.code + '</td><td>' + hit.frequency + '</td></tr>');
				});
				$('#container').append(table);
			}
		});
	});
</script>
<@footer/>