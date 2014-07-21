<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<div class="row-fluid">
	<div class="span12">
		<center>View all codes</center>
	</div>
</div>
<div class="row-fluid">
	<div id="container" class="offset3 span6">
		
	</div>
</div>
<script>
	$(document).ready(function(){
		$.ajax({
			type : 'GET',
			url :  'view/alldocs',
			contentType : 'application/json',
			success : function(data){
				var table = $('<table />').addClass('table table-bordered');
				table.append('<tr><th class="equal-width-th">Name</th><th>Frequency</th></tr>');
				$.map(data.results, function(val, key){
					table.append('<tr><td>' + key + '</th><th>' + val + '</th></tr>');
				});
				$('#container').append(table);
			}
		});
	});
</script>
<@footer/>