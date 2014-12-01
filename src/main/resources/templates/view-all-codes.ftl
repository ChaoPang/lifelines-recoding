<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<form method="GET">
<div class="row-fluid">
	<div class="span12">
	<center><h3>View all codes</h3></center>
	</div>
</div><br>
<div class="row-fluid">
	<div class="offset2 span5">
		<strong>Select a code system : </strong>
	</div>
	<div id="select-codesystem" class="span3">
		
	</div>
</div>
<div class="row-fluid">
	<div class="offset2 span5">
		<button id="download-code-button" type="button" class="btn btn-primary">Download code</button>
	</div>
</div>
<br>
<div class="row-fluid">
	<div id="container" class="offset2 span8">
	</div>
</div>
</form>
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
			$('#download-code-button').click(function(){
				$('form:first').attr({
					'method' : 'GET',
					'action' : '/view/download/' + $(select).val()
				}).submit();
			});
		});
	});
</script>
<@footer/>