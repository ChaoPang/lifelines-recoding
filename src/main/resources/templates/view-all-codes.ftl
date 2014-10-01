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
	<div id="container" class="offset2 span8">
		
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
			molgenis.retrieveAllCodes(select.val(), $('#container'));
		});
	});
</script>
<@footer/>