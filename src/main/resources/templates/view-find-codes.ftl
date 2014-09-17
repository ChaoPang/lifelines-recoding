<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<div class="row-fluid">
	<div class="span12">
	<center><h3>Find the code</h3></center>
	</div>
</div><br><br>
<div class="row-fluid">
	<div class="offset3 span6">
		<center>Please input the query and system will automatically mines the corresponding code</center>
	</div>
</div><br>
<div class="row-fluid">
	<div class="offset3 span6">
		<center>
			<div class="input-append">
				<input type="text" id="query" />
				<button id="match-button" class="btn btn-primary" type="button">Match</button>
				<button id="clear-button" class="btn btn" type="button">Clear</button>
			</div>
		</center>
	</div>
</div><br>
<div class="row-fluid">
	<div id="container" class="offset3 span6"></div>
</div>
<script>
	$(document).ready(function(){
		$('#match-button').click(function(){
			if($('#query').val() !== ''){
				molgenis.findCode($('#query').val(), null, null, function(data){
					var options = {
						'queryString' : $('#query').val(),
						'hits' : data.results,
						'parentElement' : $('#container').empty()
					};
					molgenis.createTable(options);
				});
				$('#clear-button').click(function(){
					$('#query').empty().val('');
					$('#container').empty();
				});
			}
		});
	});
</script>
<@footer/>